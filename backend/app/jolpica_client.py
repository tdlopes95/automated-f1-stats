"""
F1 Backend - Jolpica API Client
Race calendar, historical results, standings
https://api.jolpi.ca/ergast/f1/
No API key needed. Free for non-commercial use.
"""

import asyncio
import logging
import time
from datetime import UTC, datetime, timedelta

import httpx

from .errors import UpstreamError

logger = logging.getLogger(__name__)
BASE_URL = "https://api.jolpi.ca/ergast/f1"

SCHEDULE_TTL_CURRENT = 3600        # 1 hour  — current / future seasons
SCHEDULE_TTL_PAST    = 86400 * 7   # 7 days  — past seasons (immutable)

PAGE_LIMIT   = 100   # Jolpica's maximum page size
MAX_PAGES    = 30    # safety cap for paginated fetches
RESULTS_KEYS = ("Results", "QualifyingResults", "SprintResults")


class JolpicaClient:
    def __init__(self):
        self._client = httpx.AsyncClient(
            base_url=BASE_URL,
            timeout=15.0
        )
        self._semaphore = asyncio.Semaphore(4)
        # season -> (schedule, stored_at, ttl)
        self._schedule_cache: dict[int, tuple[list[dict], float, float]] = {}

    async def _get(self, path: str, params: dict = None) -> dict:
        """
        200 -> parsed JSON. 404 -> {} ("no data").
        429 -> exponential backoff; UpstreamError once retries are exhausted.
        Any other HTTP error, network error or invalid JSON -> UpstreamError.
        """
        async with self._semaphore:
            for attempt in range(4):
                try:
                    response = await self._client.get(path, params=params)
                except httpx.RequestError as e:
                    logger.error(f"Jolpica request error on {path}: {e}")
                    raise UpstreamError("jolpica", path) from e

                if response.status_code == 429:
                    wait = 2 ** attempt
                    logger.warning(f"Jolpica 429 on {path}, retrying in {wait}s")
                    await asyncio.sleep(wait)
                    continue
                if response.status_code == 404:
                    logger.info(f"Jolpica 404 on {path} (no data)")
                    return {}
                if response.status_code != 200:
                    logger.error(f"Jolpica HTTP error {response.status_code} on {path}")
                    raise UpstreamError("jolpica", path, response.status_code)

                try:
                    data = response.json()
                except ValueError as e:
                    logger.error(f"Jolpica invalid JSON on {path}: {e}")
                    raise UpstreamError("jolpica", path, response.status_code) from e
                await asyncio.sleep(0.3)
                return data

            logger.error(f"Jolpica: exhausted retries on {path}")
            raise UpstreamError("jolpica", path, 429)

    # ── Race Schedule / Calendar ─────────────────────────────────────────────

    @staticmethod
    def _resolve_year(year) -> int:
        if year is None or year == "current":
            return datetime.now(UTC).year
        return int(year)

    def invalidate_schedule(self, year: int = None):
        """Drop one cached season schedule, or all of them when year is None."""
        if year is None:
            self._schedule_cache.clear()
        else:
            self._schedule_cache.pop(self._resolve_year(year), None)

    async def get_schedule(self, year: int = None) -> list[dict]:
        """
        Returns the full race calendar for a season.
        Each item includes: raceName, Circuit, date, time, plus
        optional FirstPractice, SecondPractice, ThirdPractice,
        Qualifying, Sprint, SprintQualifying session datetimes.
        Cached in-client: 1h for the current/future seasons, 7 days for past ones.
        """
        season = self._resolve_year(year)
        entry = self._schedule_cache.get(season)
        if entry is not None:
            schedule, ts, ttl = entry
            if time.time() - ts < ttl:
                return schedule
            del self._schedule_cache[season]

        data = await self._get(f"/{season}.json", {"limit": 30})
        races = data.get("MRData", {}).get("RaceTable", {}).get("Races", [])

        schedule = []
        for race in races:
            location = race.get("Circuit", {}).get("Location", {})
            entry = {
                "round": int(race.get("round", 0)),
                "race_name": race.get("raceName"),
                "circuit": race.get("Circuit", {}).get("circuitName"),
                "circuit_id": race.get("Circuit", {}).get("circuitId"),
                "country": location.get("country"),
                "locality": location.get("locality"),
                "lat": self._to_float(location.get("lat")),
                "lng": self._to_float(location.get("long")),
                "sessions": self._parse_sessions(race),
            }
            schedule.append(entry)

        if schedule:
            current_year = datetime.now(UTC).year
            ttl = SCHEDULE_TTL_PAST if season < current_year else SCHEDULE_TTL_CURRENT
            self._schedule_cache[season] = (schedule, time.time(), ttl)
        return schedule

    def _parse_sessions(self, race: dict) -> list[dict]:
        """Extract all session datetimes from a race entry."""
        sessions = []
        session_keys = [
            ("FirstPractice", "Practice 1"),
            ("SecondPractice", "Practice 2"),
            ("ThirdPractice", "Practice 3"),
            ("SprintQualifying", "Sprint Qualifying"),
            ("Sprint", "Sprint"),
            ("Qualifying", "Qualifying"),
            ("Race", "Race"),
        ]
        # Race date/time is at top level
        race_entry = {**race}
        race_entry["date"] = race.get("date")
        race_entry["time"] = race.get("time")
        session_keys[-1] = ("Race", "Race")  # handled separately below

        for key, label in session_keys[:-1]:
            s = race.get(key)
            if s:
                dt = self._to_datetime(s.get("date"), s.get("time"))
                if dt:
                    sessions.append({"name": label, "datetime": dt.isoformat()})

        # Race itself
        race_dt = self._to_datetime(race.get("date"), race.get("time"))
        if race_dt:
            sessions.append({"name": "Race", "datetime": race_dt.isoformat()})

        return sessions

    @staticmethod
    def _to_float(value) -> float | None:
        try:
            return float(value)
        except (TypeError, ValueError):
            return None

    def _to_datetime(self, date_str: str | None, time_str: str | None) -> datetime | None:
        if not date_str:
            return None
        try:
            if time_str:
                time_str = time_str.replace("Z", "+00:00")
                return datetime.fromisoformat(f"{date_str}T{time_str}")
            return datetime.fromisoformat(date_str)
        except ValueError:
            return None

    async def get_next_race(self) -> dict | None:
        """Returns the next upcoming race from the current season."""
        schedule = await self.get_schedule()
        now = datetime.now(UTC)
        for race in schedule:
            for session in race.get("sessions", []):
                if session["name"] == "Race":
                    race_dt = datetime.fromisoformat(session["datetime"])
                    if race_dt.tzinfo is None:
                        race_dt = race_dt.replace(tzinfo=UTC)
                    if race_dt >= now:
                        return race
        return None

    async def get_upcoming_sessions(self, days_ahead: int = 14) -> list[dict]:
        """
        Returns all sessions happening within the next N days.
        Used by the scheduler to arm jobs.
        """
        schedule = await self.get_schedule()
        now = datetime.now(UTC)
        cutoff = now + timedelta(days=days_ahead)
        upcoming = []
        for race in schedule:
            for session in race.get("sessions", []):
                session_dt = datetime.fromisoformat(session["datetime"])
                if now <= session_dt <= cutoff:
                    upcoming.append({
                        "race_name": race["race_name"],
                        "country": race["country"],
                        "round": race["round"],
                        "session_name": session["name"],
                        "session_datetime": session_dt,
                    })
        return sorted(upcoming, key=lambda s: s["session_datetime"])

    # ── Results ───────────────────────────────────────────────────────────────

    async def get_race_results(self, year: int, round_number: int) -> list[dict]:
        """Get final race results for a specific round."""
        data = await self._get(f"/{year}/{round_number}/results.json")
        races = data.get("MRData", {}).get("RaceTable", {}).get("Races", [])
        if not races:
            return []
        return races[0].get("Results", [])

    async def get_qualifying_results(self, year: int, round_number: int) -> list[dict]:
        data = await self._get(f"/{year}/{round_number}/qualifying.json")
        races = data.get("MRData", {}).get("RaceTable", {}).get("Races", [])
        if not races:
            return []
        return races[0].get("QualifyingResults", [])

    async def get_sprint_results(self, year: int, round_number: int) -> list[dict]:
        data = await self._get(f"/{year}/{round_number}/sprint.json")
        races = data.get("MRData", {}).get("RaceTable", {}).get("Races", [])
        if not races:
            return []
        return races[0].get("SprintResults", [])

    async def get_last_race_results(self) -> list[dict]:
        data = await self._get("/current/last/results.json")
        races = data.get("MRData", {}).get("RaceTable", {}).get("Races", [])
        if not races:
            return []
        return races[0].get("Results", [])

    # ── Standings ─────────────────────────────────────────────────────────────

    async def get_driver_standings(self, year: int = None, round_number: int = None) -> list[dict]:
        season = str(year) if year else "current"
        path = f"/{season}"
        if round_number:
            path += f"/{round_number}"
        path += "/driverStandings.json"
        data = await self._get(path)
        standings_table = data.get("MRData", {}).get("StandingsTable", {})
        lists = standings_table.get("StandingsLists", [])
        if not lists:
            return []
        return lists[0].get("DriverStandings", [])

    async def get_constructor_standings(
        self, year: int = None, round_number: int = None
    ) -> list[dict]:
        season = str(year) if year else "current"
        path = f"/{season}"
        if round_number:
            path += f"/{round_number}"
        path += "/constructorStandings.json"
        data = await self._get(path)
        standings_table = data.get("MRData", {}).get("StandingsTable", {})
        lists = standings_table.get("StandingsLists", [])
        if not lists:
            return []
        return lists[0].get("ConstructorStandings", [])

    # ── Circuit Results ───────────────────────────────────────────────────────

    async def _get_all_races(self, path: str, params: dict = None) -> list[dict]:
        """
        Fetch every page of a RaceTable endpoint (Jolpica caps limit at 100).
        Pagination is over result rows, so one race can straddle two pages:
        races are merged by (season, round) and their result lists extended.
        """
        merged: dict[tuple, dict] = {}
        offset = 0
        for _ in range(MAX_PAGES):
            data = await self._get(path, {**(params or {}), "limit": PAGE_LIMIT, "offset": offset})
            mr_data = data.get("MRData", {})
            for race in mr_data.get("RaceTable", {}).get("Races", []):
                key = (race.get("season"), race.get("round"))
                existing = merged.get(key)
                if existing is None:
                    merged[key] = race
                    continue
                for results_key in RESULTS_KEYS:
                    if results_key in race:
                        existing.setdefault(results_key, []).extend(race[results_key])
            offset += PAGE_LIMIT
            if offset >= int(mr_data.get("total", 0)):
                break
        else:
            logger.warning(f"Jolpica: hit {MAX_PAGES}-page cap on {path}")
        return list(merged.values())

    async def get_circuit_winners(self, circuit_id: str) -> list[dict]:
        """Every race at a circuit, each with its P1 result row(s)."""
        return await self._get_all_races(f"/circuits/{circuit_id}/results/1.json")

    async def get_circuit_pole_starters(self, circuit_id: str) -> list[dict]:
        """Every race at a circuit, each with the driver(s) who started from grid 1."""
        return await self._get_all_races(f"/circuits/{circuit_id}/grid/1/results.json")

    async def get_circuit_fastest_laps(self, circuit_id: str) -> list[dict]:
        """Races at a circuit with the fastest-lap holder (lap times exist from 2004 only)."""
        return await self._get_all_races(f"/circuits/{circuit_id}/fastest/1/results.json")

    # ── Race winners ──────────────────────────────────────────────────────────

    async def get_season_winners(self, year: int) -> list[dict]:
        """Every race of a season with its P1 result row(s); one request for a modern season."""
        return await self._get_all_races(f"/{year}/results/1.json")

    async def close(self):
        await self._client.aclose()