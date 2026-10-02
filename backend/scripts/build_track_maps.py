"""
Build app/data/tracks/{circuit_id}.json: a circuit outline for every circuit on
the calendar from 2023 (OpenF1's first season) to the current year.

For each circuit the script takes the most recent OpenF1 meeting held there,
the fastest clean lap of its Qualifying session (Race as a fallback), and that
driver's /location samples over the lap. Corner numbers come from the meeting's
MultiViewer circuit_info_url. The outline is rotated like MultiViewer draws it,
flipped to screen coordinates (Y down), fitted into a centred 1000x1000 box and
simplified with Douglas-Peucker. Point 0 is the start/finish line and
sector_breaks index the points nearest the S1/S2 and S2/S3 boundaries.

The /track-map/{circuit_id} endpoint serves these files as-is. Re-run the
script when a new circuit joins the calendar (after its first race weekend, so
OpenF1 has data for it) or when a layout changes, and commit the result:

    cd backend && .venv/bin/python -m scripts.build_track_maps

OpenF1's free tier allows 30 req/min, so requests are spaced 2.5s apart and
a full run takes a few minutes.
"""

import json
import math
import statistics
import sys
import time
from datetime import UTC, datetime, timedelta

import httpx

from app.track_maps import DATA_DIR

JOLPICA_URL = "https://api.jolpi.ca/ergast/f1"
OPENF1_URL = "https://api.openf1.org/v1"
FIRST_SEASON = 2023          # OpenF1 has no data before 2023
OPENF1_DELAY = 2.5           # seconds between OpenF1 requests (free tier: 30 req/min)
RETRY_429_WAIT = 15          # seconds before the single retry after a 429
JOLPICA_DELAY = 0.5
BOX_SIZE = 1000.0
TARGET_POINTS = 300          # simplify until the outline has at most this many points
# max median corner-to-outline distance, as a share of the outline's bbox diagonal
CORNER_TOLERANCE = 0.03
LOCATION_MARGIN_S = 1.0

SCHEDULE_SESSION_KEYS = ("FirstPractice", "SecondPractice", "ThirdPractice",
                         "SprintQualifying", "Sprint", "Qualifying")


# ── Geometry (pure, unit-tested) ────────────────────────────────────────────

def transform(points: list[tuple[float, float]], corners: list[dict], rotation: float,
              box: float = BOX_SIZE) -> tuple[list[list[float]], list[dict]]:
    """
    Rotate points and corners by `rotation` degrees (counter-clockwise, as MultiViewer
    stores it), flip Y for screen coordinates, then scale uniformly and translate so the
    outline is centred in a box x box square. Corner angles are carried into the same
    screen space: degrees from +x, clockwise (Y points down).
    """
    rad = math.radians(rotation)
    cos_r, sin_r = math.cos(rad), math.sin(rad)

    def rotate_flip(x: float, y: float) -> tuple[float, float]:
        return x * cos_r - y * sin_r, -(x * sin_r + y * cos_r)

    rotated = [rotate_flip(x, y) for x, y in points]
    xs = [p[0] for p in rotated]
    ys = [p[1] for p in rotated]
    min_x, max_x, min_y, max_y = min(xs), max(xs), min(ys), max(ys)
    span = max(max_x - min_x, max_y - min_y) or 1.0
    scale = box / span
    offset_x = (box - (max_x - min_x) * scale) / 2
    offset_y = (box - (max_y - min_y) * scale) / 2

    def fit(x: float, y: float) -> list[float]:
        return [round((x - min_x) * scale + offset_x, 1), round((y - min_y) * scale + offset_y, 1)]

    out_points = [fit(x, y) for x, y in rotated]
    out_corners = []
    for c in corners:
        x, y = fit(*rotate_flip(c["x"], c["y"]))
        angle = c.get("angle")
        if angle is not None:
            angle = round(((-(angle + rotation) + 180) % 360) - 180, 1)
        out_corners.append({"number": c["number"], "letter": c.get("letter"),
                            "x": x, "y": y, "angle": angle})
    return out_points, out_corners


def _point_segment_distance(p, a, b) -> float:
    (px, py), (ax, ay), (bx, by) = p, a, b
    dx, dy = bx - ax, by - ay
    length_sq = dx * dx + dy * dy
    if length_sq == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length_sq))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def _douglas_peucker(points, first: int, last: int, epsilon: float, keep: set[int]) -> None:
    """Mark the indices in points[first..last] that survive simplification (iterative)."""
    stack = [(first, last)]
    while stack:
        start, end = stack.pop()
        keep.add(start)
        keep.add(end)
        max_dist, index = -1.0, -1
        for i in range(start + 1, end):
            dist = _point_segment_distance(points[i], points[start], points[end])
            if dist > max_dist:
                max_dist, index = dist, i
        if index != -1 and max_dist > epsilon:
            stack.append((start, index))
            stack.append((index, end))


def simplify(points: list, sector_breaks: list[int], epsilon: float) -> tuple[list, list[int]]:
    """
    Douglas-Peucker that always keeps point 0, the last point and every sector break,
    so the breaks keep pointing at the same samples. Returns (points, new sector breaks).
    """
    if len(points) < 3:
        return list(points), list(sector_breaks)
    anchors = sorted({0, len(points) - 1, *sector_breaks})
    keep: set[int] = set()
    for start, end in zip(anchors, anchors[1:], strict=False):
        _douglas_peucker(points, start, end, epsilon, keep)
    kept = sorted(keep)
    new_index = {old: new for new, old in enumerate(kept)}
    return [points[i] for i in kept], [new_index[i] for i in sector_breaks]


def simplify_to_target(points: list, sector_breaks: list[int],
                       target: int = TARGET_POINTS) -> tuple[list, list[int]]:
    """Smallest Douglas-Peucker epsilon (binary search) leaving at most `target` points."""
    if len(points) <= target:
        return list(points), list(sector_breaks)
    low, high = 0.0, BOX_SIZE
    best = simplify(points, sector_breaks, high)
    for _ in range(40):
        mid = (low + high) / 2
        candidate = simplify(points, sector_breaks, mid)
        if len(candidate[0]) <= target:
            best, high = candidate, mid
        else:
            low = mid
    return best


def corners_match_outline(corners: list[dict], outline: list[tuple[float, float]],
                          tolerance: float = CORNER_TOLERANCE) -> bool:
    """
    False when the median distance from each corner to its nearest outline point exceeds
    `tolerance` x the outline's bounding-box diagonal (corner data for another layout).
    """
    if not corners or not outline:
        return False
    xs = [p[0] for p in outline]
    ys = [p[1] for p in outline]
    diagonal = math.hypot(max(xs) - min(xs), max(ys) - min(ys))
    distances = [min(math.hypot(c["x"] - x, c["y"] - y) for x, y in outline) for c in corners]
    return statistics.median(distances) <= tolerance * diagonal


def nearest_index(times: list[datetime], target: datetime) -> int:
    return min(range(len(times)), key=lambda i: abs((times[i] - target).total_seconds()))


# ── Fetching ────────────────────────────────────────────────────────────────

def parse_dt(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    return dt if dt.tzinfo else dt.replace(tzinfo=UTC)


def normalise_country(country: str | None) -> str:
    """Same aliases as the Android MeetingMatcher."""
    c = (country or "").strip().lower()
    if c in ("usa", "united states", "united states of america"):
        return "united states"
    if c in ("uk", "united kingdom", "great britain"):
        return "united kingdom"
    if c in ("uae", "united arab emirates"):
        return "united arab emirates"
    return c


class Fetcher:
    def __init__(self, client: httpx.Client):
        self.client = client
        self._last_openf1 = 0.0

    def openf1(self, endpoint: str, query: str):
        """GET an OpenF1 endpoint with a raw query string (keeps date>= / date<= intact)."""
        for attempt in range(2):
            wait = self._last_openf1 + OPENF1_DELAY - time.monotonic()
            if wait > 0:
                time.sleep(wait)
            self._last_openf1 = time.monotonic()
            response = self.client.get(f"{OPENF1_URL}/{endpoint}?{query}")
            if response.status_code == 429 and attempt == 0:
                print(f"  OpenF1 429 on {endpoint}, retrying in {RETRY_429_WAIT}s", file=sys.stderr)
                time.sleep(RETRY_429_WAIT)
                continue
            if response.status_code == 404:
                return []           # OpenF1's "No results found"
            response.raise_for_status()
            return response.json()
        raise RuntimeError(f"OpenF1 kept rate-limiting {endpoint}")

    def jolpica_schedule(self, year: int) -> list[dict]:
        time.sleep(JOLPICA_DELAY)
        response = self.client.get(f"{JOLPICA_URL}/{year}.json", params={"limit": 100})
        response.raise_for_status()
        return response.json()["MRData"]["RaceTable"]["Races"]

    def circuit_info(self, url: str) -> dict | None:
        try:
            response = self.client.get(url)
            response.raise_for_status()
            return response.json()
        except (httpx.HTTPError, ValueError) as e:
            print(f"  circuit_info_url failed ({url}): {e}", file=sys.stderr)
            return None


def race_window(race: dict) -> tuple[datetime, datetime, datetime] | None:
    """(first session - 2 days, race + 1 day, race time) for a Jolpica schedule race."""
    def at(entry: dict) -> datetime | None:
        if not entry.get("date"):
            return None
        return parse_dt(f"{entry['date']}T{entry.get('time') or '00:00:00Z'}")

    race_dt = at(race)
    if race_dt is None:
        return None
    sessions = [at(race[k]) for k in SCHEDULE_SESSION_KEYS if race.get(k)]
    first = min([dt for dt in sessions if dt] + [race_dt])
    return first - timedelta(days=2), race_dt + timedelta(days=1), race_dt


def match_meeting(race: dict, meetings: list[dict]) -> dict | None:
    """The MeetingMatcher rule: date_start inside the race window, country validated."""
    window = race_window(race)
    if window is None:
        return None
    start, end, _ = window
    race_country = normalise_country(race.get("Circuit", {}).get("Location", {}).get("country"))
    for meeting in meetings:
        if meeting.get("is_cancelled"):
            continue
        meeting_start = parse_dt(meeting.get("date_start"))
        if meeting_start is None or not start <= meeting_start <= end:
            continue
        meeting_country = normalise_country(meeting.get("country_name"))
        if not race_country or not meeting_country or race_country == meeting_country:
            return meeting
    return None


def fastest_clean_lap(laps: list[dict]) -> dict | None:
    clean = [lap for lap in laps
             if lap.get("lap_duration") and lap.get("date_start")
             and all(lap.get(f"duration_sector_{i}") for i in (1, 2, 3))
             and not lap.get("is_pit_out_lap")]
    return min(clean, key=lambda lap: lap["lap_duration"]) if clean else None


def build_from_session(fetcher: Fetcher, session: dict) -> tuple[dict, list, list[datetime]]:
    """(lap, [(x, y)], [sample time]) for the fastest clean lap of a session; raises on failure."""
    laps = fetcher.openf1("laps", f"session_key={session['session_key']}")
    lap = fastest_clean_lap(laps)
    if lap is None:
        raise LookupError(f"no clean timed lap in {session['session_name']}")
    lap_start = parse_dt(lap["date_start"])
    lap_end = lap_start + timedelta(seconds=lap["lap_duration"] + LOCATION_MARGIN_S)
    fmt = "%Y-%m-%dT%H:%M:%S.%f"
    samples = fetcher.openf1(
        "location",
        f"session_key={session['session_key']}&driver_number={lap['driver_number']}"
        f"&date>={lap_start.astimezone(UTC).strftime(fmt)}"
        f"&date<={lap_end.astimezone(UTC).strftime(fmt)}",
    )
    samples = [s for s in samples if s.get("x") is not None and s.get("y") is not None
               and parse_dt(s.get("date"))]
    samples.sort(key=lambda s: s["date"])
    if len(samples) < 50:
        raise LookupError(f"only {len(samples)} location samples in {session['session_name']}")
    return lap, [(s["x"], s["y"]) for s in samples], [parse_dt(s["date"]) for s in samples]


def build_circuit(fetcher: Fetcher, circuit_id: str, circuit_name: str, meeting: dict,
                  sessions: list[dict]) -> tuple[dict, str]:
    """The track JSON for one circuit plus a corners note for the summary table."""
    by_name = {s.get("session_name"): s for s in sessions}
    errors = []
    for name in ("Qualifying", "Race"):
        session = by_name.get(name)
        if session is None:
            errors.append(f"no {name} session")
            continue
        try:
            lap, raw_points, times = build_from_session(fetcher, session)
            break
        except LookupError as e:
            errors.append(str(e))
    else:
        raise LookupError("; ".join(errors))

    lap_start = parse_dt(lap["date_start"])
    s1 = lap_start + timedelta(seconds=lap["duration_sector_1"])
    s2 = s1 + timedelta(seconds=lap["duration_sector_2"])
    sector_breaks = [nearest_index(times, s1), nearest_index(times, s2)]

    rotation, corners, corners_note = 0.0, [], "none (no circuit_info_url)"
    info_url = meeting.get("circuit_info_url")
    info = fetcher.circuit_info(info_url) if info_url else None
    if info is not None:
        rotation = float(info.get("rotation") or 0)
        raw_corners = [{"number": c.get("number"), "letter": c.get("letter") or None,
                        "x": c["trackPosition"]["x"], "y": c["trackPosition"]["y"],
                        "angle": c.get("angle")}
                       for c in info.get("corners", []) if c.get("trackPosition")]
        if corners_match_outline(raw_corners, raw_points):
            corners, corners_note = raw_corners, f"{len(raw_corners)} kept"
        elif raw_corners:
            corners_note = f"{len(raw_corners)} DROPPED (mismatch)"
            print(f"  WARNING {circuit_id}: corners don't match the outline, dropped",
                  file=sys.stderr)
        else:
            corners_note = "none in circuit info"
    elif info_url:
        corners_note = "none (circuit info failed)"

    points, out_corners = transform(raw_points, corners, rotation)
    points, sector_breaks = simplify_to_target(points, sector_breaks)
    return {
        "circuit_id": circuit_id,
        "circuit_name": circuit_name,
        "source": {"year": meeting["year"], "meeting_name": meeting["meeting_name"],
                   "session": session["session_name"], "driver_number": lap["driver_number"]},
        "points": points,
        "sector_breaks": sector_breaks,
        "corners": out_corners,
        "generated_at": datetime.now(UTC).isoformat(timespec="seconds"),
    }, corners_note


def main() -> None:
    now = datetime.now(UTC)
    with httpx.Client(timeout=60.0, headers={"User-Agent": "F1StatsApp/1.0 (build script)"}) as c:
        fetcher = Fetcher(c)

        # circuit_id -> (circuit name, latest meeting, its race time); plus circuits not raced yet
        latest: dict[str, tuple[str, dict, datetime]] = {}
        circuit_names: dict[str, str] = {}
        raced: set[str] = set()
        sessions_by_meeting: dict[int, list[dict]] = {}
        for year in range(FIRST_SEASON, now.year + 1):
            races = fetcher.jolpica_schedule(year)
            meetings = fetcher.openf1("meetings", f"year={year}")
            for session in fetcher.openf1("sessions", f"year={year}"):
                sessions_by_meeting.setdefault(session["meeting_key"], []).append(session)
            for race in races:
                circuit = race.get("Circuit", {})
                circuit_id = circuit.get("circuitId")
                circuit_names[circuit_id] = circuit.get("circuitName")
                window = race_window(race)
                if window is None or window[2] > now:
                    continue
                raced.add(circuit_id)
                meeting = match_meeting(race, meetings)
                if meeting is None:
                    continue
                if circuit_id not in latest or window[2] > latest[circuit_id][2]:
                    latest[circuit_id] = (circuit.get("circuitName"), meeting, window[2])
            print(f"  {year}: {len(races)} races, {len(meetings)} meetings", file=sys.stderr)

        DATA_DIR.mkdir(parents=True, exist_ok=True)
        rows, failures = [], []
        for circuit_id in sorted(circuit_names):
            if circuit_id not in raced:
                failures.append((circuit_id, "not raced yet, re-run after its first race"))
                continue
            if circuit_id not in latest:
                failures.append((circuit_id, "no matching OpenF1 meeting for a past race"))
                continue
            name, meeting, _ = latest[circuit_id]
            print(f"  {circuit_id}: {meeting['year']} {meeting['meeting_name']}", file=sys.stderr)
            try:
                track, corners_note = build_circuit(
                    fetcher, circuit_id, name, meeting,
                    sessions_by_meeting.get(meeting["meeting_key"], []))
            except (LookupError, httpx.HTTPError) as e:
                failures.append((circuit_id, str(e)))
                continue
            with (DATA_DIR / f"{circuit_id}.json").open("w", encoding="utf-8") as f:
                json.dump(track, f, ensure_ascii=False, separators=(",", ":"))
                f.write("\n")
            source = track["source"]
            rows.append((circuit_id, len(track["points"]), corners_note,
                         f"{source['year']} {source['meeting_name']} {source['session']} "
                         f"#{source['driver_number']}"))

    print(f"\n{'circuit':<16} {'points':>6}  {'corners':<26} source")
    for circuit_id, points, corners_note, source in rows:
        print(f"{circuit_id:<16} {points:>6}  {corners_note:<26} {source}")
    if failures:
        print("\nFailed:")
        for circuit_id, reason in failures:
            print(f"  {circuit_id}: {reason}")
    print(f"\nWrote {len(rows)} track maps to {DATA_DIR}")


if __name__ == "__main__":
    main()
