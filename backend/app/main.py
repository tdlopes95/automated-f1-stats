"""
F1 Backend - FastAPI Main Application
Run with: uvicorn app.main:app --reload --port 8000
"""

from dotenv import load_dotenv

# Must run before anything reads os.getenv (database.py reads DB_PATH at import time).
load_dotenv()

import asyncio
import logging
import os
import time
from contextlib import asynccontextmanager
from datetime import UTC, date, datetime, timedelta
from typing import Annotated

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded

from . import history, track_maps
from .database import Database
from .errors import UpstreamError
from .jolpica_client import JolpicaClient
from .models import (
    CircuitPitHistoryResponse,
    CircuitStatsResponse,
    ConstructorStandingsResponse,
    DriverInfo,
    DriverStandingsResponse,
    MeetingInfo,
    NewsResponse,
    OnThisDayResponse,
    RaceAnalysisResponse,
    RaceSchedule,
    ResultsResponse,
    TrackMapResponse,
    WeatherForecastResponse,
)
from .news_client import MAX_ITEMS as NEWS_MAX_ITEMS
from .news_client import NewsClient
from .openf1_client import OpenF1Client
from .scheduler import F1Scheduler
from .weather_client import ATTRIBUTION as WEATHER_ATTRIBUTION
from .weather_client import (
    FORECAST_HORIZON_DAYS,
    OpenMeteoClient,
    forecast_end_date,
    map_days,
    map_sessions,
)


def client_ip(request: Request) -> str:
    """
    The real client IP. The app runs behind Cloudflare and Koyeb's proxy, so
    request.client.host is the proxy's address and would put everyone in one bucket.
    """
    cf_ip = request.headers.get("cf-connecting-ip", "").strip()
    if cf_ip:
        return cf_ip
    forwarded = request.headers.get("x-forwarded-for", "").split(",")[0].strip()
    if forwarded:
        return forwarded
    return request.client.host if request.client else "127.0.0.1"


limiter = Limiter(key_func=client_ip)

# ── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO").upper(),
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s"
)
logger = logging.getLogger(__name__)

# ── In-memory cache ───────────────────────────────────────────────────────────
_cache: dict = {}
CACHE_TTL           = 300        # 5 minutes  — live/current data
CACHE_TTL_STANDINGS = 1800       # 30 minutes — current-season standings
CACHE_TTL_FOREVER   = 86400 * 7  # 7 days     — historical data (never changes)
CACHE_TTL_WEATHER   = 3600       # 1 hour     — race weekend forecast
CACHE_TTL_NEWS      = 900        # 15 minutes — news headlines
CACHE_TTL_HISTORY   = 3600       # 1 hour     — current-season race winners

# Current-season results for a race this recent may still change (post-race penalties)
RESULTS_VOLATILE_WINDOW = timedelta(hours=72)
RESULTS_VOLATILE_MAX_AGE = timedelta(hours=1)

# Live snapshots
LIVE_STORED_MAX_AGE = timedelta(minutes=2)   # a stored (poller) snapshot is served if newer
LIVE_IS_LIVE_MAX_AGE = timedelta(seconds=60) # ...and flagged is_live only if newer than this
LATEST_SESSION_CACHE_KEY = "latest_session"

def cache_get(key: str):
    entry = _cache.get(key)
    if entry is None:
        return None
    data, ts, ttl = entry
    if time.time() - ts < ttl:
        return data
    del _cache[key]
    return None

def cache_set(key: str, data, ttl: float = CACHE_TTL):
    _cache[key] = (data, time.time(), ttl)

def cache_set_historical(key: str, data):
    cache_set(key, data, CACHE_TTL_FOREVER)

def cache_invalidate(prefix: str):
    for key in [k for k in _cache if k.startswith(prefix)]:
        del _cache[key]

def add_gap_to_second(standings: list) -> list:
    if not standings or len(standings) < 2:
        return standings
    try:
        p1_pts = float(standings[0].get("points", 0))
        p2_pts = float(standings[1].get("points", 0))
        standings[0]["gap_to_second"] = round(p1_pts - p2_pts, 1)
    except Exception:
        standings[0]["gap_to_second"] = 0
    return standings

def race_datetime(race: dict) -> datetime | None:
    """UTC datetime of a schedule entry's Race session, or None."""
    for session in race.get("sessions", []):
        if session.get("name") == "Race" and session.get("datetime"):
            try:
                dt = datetime.fromisoformat(session["datetime"])
            except ValueError:
                return None
            return dt if dt.tzinfo else dt.replace(tzinfo=UTC)
    return None

def last_completed_round(schedule: list, now: datetime | None = None) -> int:
    """Highest round whose Race start is at or before now (UTC); 0 if none."""
    now = now or datetime.now(UTC)
    last_round = 0
    for race in schedule:
        dt = race_datetime(race)
        if dt and dt <= now:
            last_round = max(last_round, int(race.get("round", 0)))
    return last_round

def final_round(schedule: list) -> int:
    return max((int(r.get("round", 0)) for r in schedule), default=0)

async def fetch_results(year: int, round_number: int, session_type: str) -> list:
    if session_type == "Race":
        return await jolpica.get_race_results(year, round_number)
    if session_type == "Qualifying":
        return await jolpica.get_qualifying_results(year, round_number)
    return await jolpica.get_sprint_results(year, round_number)

# ── Globals ───────────────────────────────────────────────────────────────────
db: Database        = None
jolpica: JolpicaClient  = None
openf1: OpenF1Client    = None
openmeteo: OpenMeteoClient = None
news: NewsClient        = None
scheduler: F1Scheduler  = None


async def resolve_latest_session(force_refresh: bool = False) -> dict | None:
    """OpenF1's latest session, cached in memory for 5 minutes."""
    cached = None if force_refresh else cache_get(LATEST_SESSION_CACHE_KEY)
    if cached is not None:
        return cached or None
    session = await openf1.get_latest_session()
    cache_set(LATEST_SESSION_CACHE_KEY, session or {})
    return session

def session_in_progress(session: dict | None, now: datetime | None = None) -> bool:
    """True if the session has a date_end that is still in the future."""
    if not session or not session.get("date_end"):
        return False
    try:
        end = datetime.fromisoformat(session["date_end"])
    except (TypeError, ValueError):
        return False
    if end.tzinfo is None:
        end = end.replace(tzinfo=UTC)
    return end > (now or datetime.now(UTC))

# ── Scheduler callbacks ───────────────────────────────────────────────────────

async def on_live_poll(session_name: str, race_name: str):
    try:
        # A cached session that has ended is the previous one; refetch so a session
        # that just started is picked up now rather than when the cache expires.
        cached = cache_get(LATEST_SESSION_CACHE_KEY)
        session = await resolve_latest_session(force_refresh=not session_in_progress(cached))
        if not session:
            return
        session_key = session.get("session_key")
        await db.upsert_session(session)
        snapshot = await openf1.get_live_snapshot(session_key)
        snapshot["session_name"] = session_name
        await db.save_snapshot(session_key, snapshot)
        logger.info(f"[LIVE] Snapshot saved for session {session_key}")
    except UpstreamError as e:
        logger.error(f"on_live_poll upstream error: {e}")
    except Exception as e:
        logger.error(f"on_live_poll error: {e}")


async def on_session_ended(session_name: str, race_name: str, round_number: int, year: int):
    try:
        if session_name in ("Race", "Qualifying", "Sprint"):
            try:
                results = await fetch_results(year, round_number, session_name)
                if results:
                    await db.save_results(year, round_number, session_name, results)
                    logger.info(f"[RESULTS] {session_name} results saved for "
                                f"{race_name} R{round_number}")
                else:
                    logger.warning(f"[RESULTS] {session_name} results for {race_name} "
                                   f"R{round_number} not available yet from Jolpica")
            finally:
                # Standings are not saved here: Jolpica often lags the flag by more than
                # 30 minutes. Dropping the caches makes the next request fetch fresh data.
                cache_invalidate(f"driver_standings_{year}")
                cache_invalidate(f"constructor_standings_{year}")
                cache_invalidate("results_latest_")
                cache_invalidate(f"results_{year}_{round_number}_")
                jolpica.invalidate_schedule()
        cache_invalidate(LATEST_SESSION_CACHE_KEY)
    except UpstreamError as e:
        logger.error(f"on_session_ended upstream error: {e}")
    except Exception as e:
        logger.error(f"on_session_ended error: {e}")


async def on_prune():
    await db.prune_snapshots(older_than_hours=48)


# ── App Lifespan ──────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    global db, jolpica, openf1, openmeteo, news, scheduler
    db = Database()
    await db.connect()
    jolpica  = JolpicaClient()
    openf1   = OpenF1Client(access_token=os.getenv("OPENF1_TOKEN"))
    openmeteo = OpenMeteoClient()
    news     = NewsClient()
    history.load_race_winners()
    track_maps.load_track_maps()
    scheduler = F1Scheduler(
        jolpica=jolpica,
        on_live_poll=on_live_poll,
        on_session_ended=on_session_ended,
        live_polling_enabled=bool(os.getenv("OPENF1_TOKEN")),
        on_prune=on_prune,
    )
    # Best-effort only: Koyeb's free tier scales to zero after an hour without traffic,
    # so these jobs run only while the instance is awake (and the SQLite file doesn't
    # survive a sleep). Correctness relies on the TTL caches, not on the jobs running.
    await scheduler.start()
    logger.info("F1 Backend started and ready.")
    yield
    scheduler.stop()
    await jolpica.close()
    await openf1.close()
    await openmeteo.close()
    await news.close()
    await db.close()
    logger.info("F1 Backend shut down.")


# ── FastAPI App ───────────────────────────────────────────────────────────────

app = FastAPI(
    title="F1 Backend API",
    description="Personal F1 data backend — race results, quali, sprint, live timing",
    version="1.0.0",
    lifespan=lifespan,
)

app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)


@app.exception_handler(UpstreamError)
async def upstream_error_handler(request: Request, exc: UpstreamError):
    logger.error(f"Upstream failure serving {request.url.path}: {exc}")
    return JSONResponse(
        status_code=502,
        content={"detail": "Upstream data source unavailable", "source": exc.source},
    )

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)


# ── Routes ────────────────────────────────────────────────────────────────────

@app.get("/")
async def root():
    return {"status": "ok", "service": "F1 Backend API"}


# ── Schedule ──────────────────────────────────────────────────────────────────

@app.get("/schedule", response_model=list[RaceSchedule])
@limiter.limit("30/minute")
async def get_schedule(request: Request, year: int | None = None):
    # Cached inside JolpicaClient (1h current/future, 7 days past seasons).
    return await jolpica.get_schedule(year)


@app.get("/schedule/next")
@limiter.limit("30/minute")
async def get_next_race(request: Request):
    race = await jolpica.get_next_race()
    if not race:
        raise HTTPException(status_code=404, detail="No upcoming race found")
    return race


@app.get("/schedule/upcoming-sessions")
@limiter.limit("30/minute")
async def get_upcoming_sessions(request: Request, days: int = Query(default=14, le=30)):
    return await jolpica.get_upcoming_sessions(days_ahead=days)


# ── Live Session ──────────────────────────────────────────────────────────────

async def get_snapshot(session_key: int) -> dict | None:
    """
    A poller-saved snapshot if one was captured in the last 2 minutes, otherwise an
    on-demand snapshot built from OpenF1 (memory-cached for 5 minutes, never stored).
    is_live is true only for a poller snapshot less than 60 seconds old.
    """
    stored = await db.get_latest_snapshot_entry(session_key)
    if stored:
        age = datetime.now(UTC) - stored["captured_at"]
        if age < LIVE_STORED_MAX_AGE:
            return {**stored["snapshot"], "is_live": age < LIVE_IS_LIVE_MAX_AGE}

    cache_key = f"live_snapshot_{session_key}"
    snapshot = cache_get(cache_key)
    if snapshot is None:
        snapshot = await openf1.get_live_snapshot(session_key)
        if not snapshot.get("drivers") and not snapshot.get("session_name"):
            return None
        cache_set(cache_key, snapshot)
    return {**snapshot, "is_live": False}


@app.get("/live")
@limiter.limit("60/minute")
async def get_live_session(request: Request):
    session = await resolve_latest_session()
    if not session or not session.get("session_key"):
        raise HTTPException(status_code=404, detail="No active session found")
    snapshot = await get_snapshot(session["session_key"])
    if snapshot is None:
        raise HTTPException(status_code=404, detail="No active session found")
    return snapshot


@app.get("/live/{session_key}")
@limiter.limit("60/minute")
async def get_live_by_session(request: Request, session_key: int):
    snapshot = await get_snapshot(session_key)
    if snapshot is None:
        raise HTTPException(status_code=404, detail="Session not found")
    return snapshot


# ── Results ───────────────────────────────────────────────────────────────────

@app.get("/results/latest")
@limiter.limit("30/minute")
async def get_latest_results(
    request: Request,
    session_type: str = Query(default="Race", enum=["Race", "Qualifying", "Sprint"]),
    year: int | None = None
):
    """
    Latest results. With no year (or the current year): the last completed round of
    the current season, falling back to last season's final round if it hasn't
    started. With a past year: that season's final round.
    """
    current_year = datetime.now(UTC).year
    cache_key = f"results_latest_{session_type}_{year if year is not None else 'default'}"
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    if year is not None and year < current_year:
        target_year = year
        schedule = await jolpica.get_schedule(target_year)
        target_round = final_round(schedule)
    else:
        target_year = year if year is not None else current_year
        schedule = await jolpica.get_schedule(target_year)
        target_round = last_completed_round(schedule)
        if target_round == 0 and target_year == current_year:
            target_year = current_year - 1
            schedule = await jolpica.get_schedule(target_year)
            target_round = final_round(schedule)

    if not schedule:
        raise HTTPException(status_code=404, detail="No schedule found")
    if target_round == 0:
        raise HTTPException(status_code=404, detail="No results found")

    race_name = next(
        (r.get("race_name", "") for r in schedule if int(r.get("round", 0)) == target_round), ""
    )
    results = await fetch_results(target_year, target_round, session_type)
    if not results:
        raise HTTPException(status_code=404, detail="No results found")

    response = {"source": "live", "year": target_year, "round": target_round,
                "session_type": session_type, "race_name": race_name, "results": results}
    cache_set(cache_key, response)
    return response


async def _results_row_is_fresh(year: int, round_number: int, fetched_at: datetime) -> bool:
    """
    Current-season rows for a race in (or shortly after) its weekend may still
    change (post-race penalties), so only trust them if recently fetched.
    """
    try:
        schedule = await jolpica.get_schedule(year)
    except UpstreamError:
        return True  # can't tell; a stored row beats a 502
    race = next((r for r in schedule if int(r.get("round", 0)) == round_number), None)
    race_dt = race_datetime(race) if race else None
    now = datetime.now(UTC)
    if race_dt is None or now - race_dt >= RESULTS_VOLATILE_WINDOW:
        return True
    return now - fetched_at < RESULTS_VOLATILE_MAX_AGE


async def _race_name_for_round(year: int, round_number: int) -> str | None:
    """Race name from the (client-cached) schedule; None if it can't be resolved."""
    try:
        schedule = await jolpica.get_schedule(year)
        return next(
            (r.get("race_name") for r in schedule if int(r.get("round", 0)) == round_number), None
        )
    except Exception as e:
        logger.warning(f"Race name lookup failed for {year} round {round_number}: {e}")
        return None


@app.get("/results/{year}/{round}", response_model=ResultsResponse)
@limiter.limit("60/minute")
async def get_results(
    request: Request,
    year: int,
    round: int,
    session_type: str = Query(default="Race", enum=["Race", "Qualifying", "Sprint"])
):
    current_year = datetime.now(UTC).year
    cache_key = f"results_{year}_{round}_{session_type}"
    if year < current_year:
        mem_cached = cache_get(cache_key)
        if mem_cached is not None:
            return mem_cached

    race_name = await _race_name_for_round(year, round)

    def response(source: str, results: list) -> dict:
        return {"source": source, "year": year, "round": round,
                "session_type": session_type, "race_name": race_name, "results": results}

    stored = await db.get_results_entry(year, round, session_type)
    if stored:
        stored_response = response("cache", stored["results"])
        if year < current_year or await _results_row_is_fresh(year, round, stored["fetched_at"]):
            return stored_response

    try:
        results = await fetch_results(year, round, session_type)
    except UpstreamError:
        if stored:
            return {**stored_response, "source": "stale"}
        raise

    if not results:
        if stored:
            return stored_response
        return response("live", results)

    await db.save_results(year, round, session_type, results)
    if year < current_year:
        cache_set_historical(cache_key, response("cache", results))

    return response("live", results)


# ── Standings ─────────────────────────────────────────────────────────────────

@app.get("/standings/drivers", response_model=DriverStandingsResponse)
@limiter.limit("30/minute")
async def get_driver_standings(request: Request, year: int | None = None):
    current_year = datetime.now(UTC).year
    target_year  = year or current_year
    cache_key    = f"driver_standings_{target_year}"

    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    if target_year != current_year:
        standings = add_gap_to_second(await jolpica.get_driver_standings(target_year))
        if standings:
            cache_set_historical(
                cache_key,
                {"source": "cache", "season_started": True, "standings": standings}
            )
        return {"source": "live", "season_started": True, "standings": standings}

    try:
        schedule = await jolpica.get_schedule(current_year)
        season_started = last_completed_round(schedule) > 0
        standings = await jolpica.get_driver_standings(current_year)
    except UpstreamError:
        stale = await db.get_latest_driver_standings(current_year)
        if stale:
            logger.warning("Serving stale driver standings from SQLite")
            return {"source": "stale", "year": current_year,
                    "season_started": True, "standings": stale}
        raise

    if not standings:
        # Season hasn't started (or Jolpica has nothing yet) -> last season's final table
        standings = add_gap_to_second(await jolpica.get_driver_standings(current_year - 1))
        response = {"source": "fallback", "year": current_year - 1,
                    "season_started": False, "standings": standings}
        if standings:
            cache_set(cache_key, response, CACHE_TTL_STANDINGS)
        return response

    standings = add_gap_to_second(standings)
    response = {"source": "live", "year": current_year,
                "season_started": season_started, "standings": standings}
    cache_set(cache_key, response, CACHE_TTL_STANDINGS)
    await db.save_driver_standings(current_year, None, standings)
    return response


@app.get("/standings/constructors", response_model=ConstructorStandingsResponse)
@limiter.limit("30/minute")
async def get_constructor_standings(request: Request, year: int | None = None):
    current_year = datetime.now(UTC).year
    target_year  = year or current_year
    cache_key    = f"constructor_standings_{target_year}"

    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    if target_year != current_year:
        standings = await jolpica.get_constructor_standings(target_year)
        if standings:
            cache_set_historical(cache_key, {"source": "cache", "standings": standings})
        return {"source": "live", "standings": standings}

    try:
        standings = await jolpica.get_constructor_standings(current_year)
    except UpstreamError:
        stale = await db.get_latest_constructor_standings(current_year)
        if stale:
            logger.warning("Serving stale constructor standings from SQLite")
            return {"source": "stale", "year": current_year, "standings": stale}
        raise

    if not standings:
        standings = await jolpica.get_constructor_standings(current_year - 1)
        response = {"source": "fallback", "year": current_year - 1, "standings": standings}
        if standings:
            cache_set(cache_key, response, CACHE_TTL_STANDINGS)
        return response

    response = {"source": "live", "year": current_year, "standings": standings}
    cache_set(cache_key, response, CACHE_TTL_STANDINGS)
    await db.save_constructor_standings(current_year, None, standings)
    return response


# ── Session Details (OpenF1) ──────────────────────────────────────────────────

@app.get("/sessions")
@limiter.limit("30/minute")
async def get_sessions(request: Request, year: int | None = None, session_type: str | None = None):
    return await openf1.get_sessions(year=year, session_type=session_type)


@app.get("/sessions/{session_key}/laps")
@limiter.limit("30/minute")
async def get_session_laps(request: Request, session_key: int, driver_number: int | None = None):
    return await openf1.get_laps(session_key, driver_number=driver_number)


@app.get("/sessions/{session_key}/fastest-laps")
@limiter.limit("30/minute")
async def get_fastest_laps(request: Request, session_key: int):
    return await openf1.get_fastest_laps(session_key)


@app.get("/sessions/{session_key}/stints")
@limiter.limit("30/minute")
async def get_stints(request: Request, session_key: int):
    cache_key = f"stints_{session_key}"
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    stints_raw, drivers_raw = await asyncio.gather(
        openf1.get_stints(session_key),
        openf1.get_drivers(session_key),
    )

    driver_lookup = {}
    for d in drivers_raw:
        num = d["driver_number"]
        driver_lookup[num] = {
            "code":         d.get("name_acronym", f"{num}"),
            "team_colour":  "#" + d.get("team_colour", "FFFFFF"),
        }

    grouped: dict[int, list] = {}
    for s in stints_raw:
        num = s.get("driver_number")
        if num is None:
            continue
        grouped.setdefault(num, []).append({
            "stint_number":      s.get("stint_number"),
            "compound":          s.get("compound"),
            "lap_start":         s.get("lap_start"),
            "lap_end":           s.get("lap_end"),
            "tyre_age_at_start": s.get("tyre_age_at_start"),
        })

    result = []
    for num, stints in grouped.items():
        stints.sort(key=lambda s: s.get("stint_number") or 0)
        info = driver_lookup.get(num, {"code": f"{num}", "team_colour": "#FFFFFF"})
        result.append({
            "driver_number": num,
            "code":          info["code"],
            "team_colour":   info["team_colour"],
            "stints":        stints,
        })

    result.sort(key=lambda d: d["driver_number"])
    if result:
        cache_set_historical(cache_key, result)
    return result


@app.get("/sessions/{session_key}/pit-stops")
@limiter.limit("30/minute")
async def get_pit_stops(request: Request, session_key: int):
    cache_key = f"pit_stops_{session_key}"
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    pit_stops_raw = await openf1.get_pit_stops(session_key)
    drivers_raw   = await openf1.get_drivers(session_key)

    driver_lookup = {}
    for d in drivers_raw:
        driver_lookup[d["driver_number"]] = {
            "name":   d.get("full_name", ""),
            "team":   d.get("team_name", ""),
            "colour": "#" + d.get("team_colour", "FFFFFF")
        }

    real_stops = [p for p in pit_stops_raw if p.get("stop_duration") is not None]

    fastest = {}
    for stop in real_stops:
        num      = stop["driver_number"]
        duration = stop.get("stop_duration", 999)
        if num not in fastest or duration < fastest[num]["stop_duration"]:
            fastest[num] = stop

    result = []
    for num, stop in fastest.items():
        driver_info = driver_lookup.get(num, {})
        result.append({
            "driver_number": num,
            "driver_name":   driver_info.get("name", f"Driver {num}"),
            "team_name":     driver_info.get("team", ""),
            "team_colour":   driver_info.get("colour", "#FFFFFF"),
            "lap_number":    stop.get("lap_number"),
            "stop_duration": stop.get("stop_duration"),
        })

    result.sort(key=lambda x: x["stop_duration"])
    cache_set(cache_key, result)
    return result


@app.get("/sessions/{session_key}/race-control")
@limiter.limit("30/minute")
async def get_race_control(request: Request, session_key: int):
    return await openf1.get_race_control(session_key)


@app.get("/sessions/{session_key}/weather")
@limiter.limit("30/minute")
async def get_weather(request: Request, session_key: int):
    return await openf1.get_latest_weather(session_key)


@app.get("/sessions/{session_key}/drivers")
@limiter.limit("30/minute")
async def get_drivers(request: Request, session_key: int):
    return await openf1.get_drivers(session_key)


@app.get("/session-key/{year}/{round}")
@limiter.limit("30/minute")
async def get_session_key(request: Request, year: int, round: int):
    cache_key = f"session_key_{year}_{round}"
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    schedule = await jolpica.get_schedule(year)
    race = next((r for r in schedule if r["round"] == round), None)
    if not race:
        raise HTTPException(status_code=404, detail="Round not found")

    race_date = None
    for session in race.get("sessions", []):
        if session["name"] == "Race":
            race_date = session["datetime"][:10]
            break

    if not race_date:
        raise HTTPException(status_code=404, detail="Race date not found")

    sessions = await openf1.get_sessions(year=year, session_type="Race")
    for session in sessions:
        if session.get("date_start", "").startswith(race_date):
            result = {"session_key": session["session_key"]}
            cache_set_historical(cache_key, result)
            return result

    raise HTTPException(status_code=404, detail="Session key not found")


# ── Meetings (OpenF1) ─────────────────────────────────────────────────────────

@app.get("/meetings", response_model=list[MeetingInfo])
@limiter.limit("30/minute")
async def get_meetings(request: Request, year: int | None = None):
    current_year = datetime.now(UTC).year
    target_year = year or current_year
    cache_key = f"meetings_{target_year}"
    cached = cache_get(cache_key)
    if cached is not None:
        return cached
    meetings = await openf1.get_meetings(target_year)
    result = []
    for m in meetings:
        result.append({
            "meeting_key":        m.get("meeting_key"),
            "meeting_name":       m.get("meeting_name"),
            "location":           m.get("location"),
            "country_name":       m.get("country_name"),
            "country_flag":       m.get("country_flag"),
            "circuit_short_name": m.get("circuit_short_name"),
            "circuit_type":       m.get("circuit_type"),
            "circuit_image":      m.get("circuit_image"),
            "gmt_offset":         m.get("gmt_offset"),
            "date_start":         m.get("date_start"),
            "year":               m.get("year"),
        })
    if target_year < current_year:
        cache_set_historical(cache_key, result)
    else:
        cache_set(cache_key, result)
    return result


# ── Drivers (OpenF1 headshots) ────────────────────────────────────────────────

@app.get("/drivers/{year}", response_model=list[DriverInfo])
@limiter.limit("30/minute")
async def get_drivers_by_year(request: Request, year: int):
    current_year = datetime.now(UTC).year
    cache_key = f"drivers_{year}"
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    if year == current_year:
        session = await openf1.get_latest_session()
    else:
        sessions = await openf1.get_sessions(year=year, session_type="Race")
        session = sessions[-1] if sessions else None

    if not session:
        raise HTTPException(status_code=404, detail=f"No sessions found for {year}")

    session_key = session.get("session_key")
    drivers_raw = await openf1.get_drivers(session_key)

    result = [
        {
            "driver_number": d.get("driver_number"),
            "name_acronym":  d.get("name_acronym"),
            "full_name":     d.get("full_name"),
            "headshot_url":  d.get("headshot_url"),
            "team_name":     d.get("team_name"),
            "team_colour":   "#" + d["team_colour"] if d.get("team_colour") else None,
        }
        for d in drivers_raw
    ]

    if year < current_year:
        cache_set_historical(cache_key, result)
    else:
        cache_set(cache_key, result)
    return result


# ── Circuit Stats ─────────────────────────────────────────────────────────────

def _parse_lap_time(time_str: str) -> float:
    """Convert '1:21.046' to total seconds for comparison."""
    try:
        parts = time_str.split(":")
        if len(parts) == 2:
            return int(parts[0]) * 60 + float(parts[1])
        return float(time_str)
    except Exception:
        return float("inf")


CIRCUIT_STATS_TTL_ACTIVE = 86400  # 24h for circuits raced this season
CIRCUIT_DATA_NOTE = (
    "Poles counted as starts from grid 1. Lap record is the fastest race lap since 2004."
)


def _unique_races(races: list) -> dict:
    """(season, round) -> race, dropping any duplicate race entries."""
    unique: dict = {}
    for race in races:
        unique.setdefault((int(race.get("season", 0)), int(race.get("round", 0))), race)
    return unique


def _tally_drivers(races: dict) -> dict:
    """driverId -> {"name", "seasons"}: one entry per race a driver appears in."""
    tally: dict = {}
    for (season, _), race in races.items():
        seen = set()
        for result in race.get("Results", []):
            driver = result.get("Driver", {})
            driver_id = driver.get("driverId", "")
            if not driver_id or driver_id in seen:
                continue
            seen.add(driver_id)
            entry = tally.setdefault(driver_id, {
                "name": f"{driver.get('givenName', '')} {driver.get('familyName', '')}".strip(),
                "seasons": [],
            })
            entry["seasons"].append(season)
    return tally


def _tally_constructors(races: dict) -> dict:
    tally: dict = {}
    for (season, _), race in races.items():
        seen = set()
        for result in race.get("Results", []):
            constructor = result.get("Constructor", {})
            constructor_id = constructor.get("constructorId", "")
            if not constructor_id or constructor_id in seen:
                continue
            seen.add(constructor_id)
            entry = tally.setdefault(
                constructor_id, {"name": constructor.get("name", ""), "seasons": []}
            )
            entry["seasons"].append(season)
    return tally


def _leader(tally: dict):
    """(id, entry) with the highest count; ties go to the most recent season."""
    if not tally:
        return None
    return max(tally.items(), key=lambda kv: (len(kv[1]["seasons"]), max(kv[1]["seasons"])))


def compute_circuit_stats(circuit_id: str, winners: list, pole_starters: list,
                          fastest_laps: list) -> dict:
    winner_races = _unique_races(winners)
    seasons = [season for season, _ in winner_races]

    circuit_meta = winners[0].get("Circuit", {}) if winners else {}
    location = circuit_meta.get("Location", {})

    most_wins = None
    leader = _leader(_tally_drivers(winner_races))
    if leader:
        driver_id, entry = leader
        most_wins = {"driverId": driver_id, "name": entry["name"],
                     "count": len(entry["seasons"]), "years": sorted(entry["seasons"])}

    most_poles = None
    leader = _leader(_tally_drivers(_unique_races(pole_starters)))
    if leader:
        driver_id, entry = leader
        most_poles = {"driverId": driver_id, "name": entry["name"], "count": len(entry["seasons"])}

    most_constructor_wins = None
    leader = _leader(_tally_constructors(winner_races))
    if leader:
        constructor_id, entry = leader
        most_constructor_wins = {"constructorId": constructor_id, "name": entry["name"],
                                 "count": len(entry["seasons"])}

    # Pre-2004 races can appear here without a lap time; only timed laps count.
    lap_records = []
    for (season, _), race in _unique_races(fastest_laps).items():
        for result in race.get("Results", []):
            fl_time = (result.get("FastestLap") or {}).get("Time", {}).get("time", "")
            secs = _parse_lap_time(fl_time) if fl_time else float("inf")
            if secs != float("inf"):
                driver = result.get("Driver", {})
                name = f"{driver.get('givenName', '')} {driver.get('familyName', '')}".strip()
                lap_records.append((secs, driver.get("driverId", ""), name, season, fl_time))

    lap_record = None
    if lap_records:
        best = min(lap_records, key=lambda x: x[0])
        lap_record = {"driverId": best[1], "name": best[2], "time": best[4], "year": best[3]}

    return {
        "circuitId": circuit_id,
        "circuitName": circuit_meta.get("circuitName", ""),
        "locality": location.get("locality", ""),
        "country": location.get("country", ""),
        "totalRaces": len(winner_races),
        "firstGPYear": min(seasons) if seasons else 0,
        "lastGPYear": max(seasons) if seasons else 0,
        "mostWins": most_wins,
        "mostPoles": most_poles,
        "mostConstructorWins": most_constructor_wins,
        "lapRecord": lap_record,
        "lapRecordSinceYear": min(r[3] for r in lap_records) if lap_records else None,
        "dataNote": CIRCUIT_DATA_NOTE,
    }


@app.get("/circuit/{circuit_id}/stats", response_model=CircuitStatsResponse)
@limiter.limit("30/minute")
async def get_circuit_stats(request: Request, circuit_id: str):
    cache_key = f"circuit_stats:{circuit_id}"
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    # UpstreamError from any of these propagates -> 502 via the global handler.
    winners, pole_starters, fastest_laps = await asyncio.gather(
        jolpica.get_circuit_winners(circuit_id),
        jolpica.get_circuit_pole_starters(circuit_id),
        jolpica.get_circuit_fastest_laps(circuit_id),
    )

    if not winners:
        raise HTTPException(status_code=404, detail=f"No races found for circuit '{circuit_id}'")

    stats = compute_circuit_stats(circuit_id, winners, pole_starters, fastest_laps)
    if stats["lastGPYear"] == datetime.now(UTC).year:
        cache_set(cache_key, stats, CIRCUIT_STATS_TTL_ACTIVE)
    else:
        cache_set_historical(cache_key, stats)
    return stats


# ── Race analysis ─────────────────────────────────────────────────────────────

LAPS_FIRST_SEASON = 1996
PIT_STOPS_FIRST_SEASON = 2011
PIT_HISTORY_MAX_SEASONS = 15
PIT_DURATION_NOTE = (
    "Pit stop durations are pit-lane times (entry to exit), not stationary times."
)


def parse_duration_ms(value) -> int | None:
    """'1:23.456', '23.456' or '1:02:03.456' -> milliseconds; None if unparseable."""
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        total = 0.0
        for part in value.strip().split(":"):
            number = float(part)
            if number < 0:
                return None
            total = total * 60 + number
    except ValueError:
        return None
    return round(total * 1000)


def _to_int(value) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _driver_name(driver: dict) -> str:
    return f"{driver.get('givenName', '')} {driver.get('familyName', '')}".strip()


def is_finisher(status: str | None) -> bool:
    """Classified at the flag: 'Finished', '+1 Lap', '+2 Laps', 'Lapped'."""
    status = status or ""
    return status == "Finished" or status.startswith("+") or "Lap" in status


def build_race_analysis(year: int, round_number: int, race_name: str | None,
                        results: list, laps: list, pit_stops: list,
                        laps_fetched: bool) -> dict:
    ordered = sorted(results, key=lambda r: _to_int(r.get("position")) or 999)
    drivers = []
    for result in ordered:
        driver = result.get("Driver", {})
        constructor = result.get("Constructor", {})
        drivers.append({
            "driver_id": driver.get("driverId", ""),
            "code": driver.get("code"),
            "name": _driver_name(driver),
            "constructor_id": constructor.get("constructorId"),
            "constructor_name": constructor.get("name"),
            "grid": _to_int(result.get("grid")),
            "final_position": _to_int(result.get("position")),
            "status": result.get("status"),
        })

    lap_numbers = [lap["number"] for lap in laps if lap.get("number", 0) > 0]
    result_laps = [_to_int(r.get("laps")) or 0 for r in results]
    total_laps = max(lap_numbers) if lap_numbers else max(result_laps, default=0)

    positions: dict[str, list] = {}
    lap_times: dict[str, list] = {}
    laps_available = laps_fetched and bool(lap_numbers)
    if laps_available:
        # Classified drivers first, then anyone with timings but no result row
        driver_ids = dict.fromkeys(d["driver_id"] for d in drivers if d["driver_id"])
        for lap in laps:
            for timing in lap.get("Timings", []):
                if timing.get("driverId"):
                    driver_ids.setdefault(timing["driverId"])
        positions = {d: [None] * total_laps for d in driver_ids}
        lap_times = {d: [None] * total_laps for d in driver_ids}
        for lap in laps:
            index = lap.get("number", 0) - 1
            if not 0 <= index < total_laps:
                continue
            for timing in lap.get("Timings", []):
                driver_id = timing.get("driverId")
                if driver_id not in positions:
                    continue
                positions[driver_id][index] = _to_int(timing.get("position"))
                lap_times[driver_id][index] = parse_duration_ms(timing.get("time"))

    stops = [{
        "driver_id": stop.get("driverId", ""),
        "stop": _to_int(stop.get("stop")),
        "lap": _to_int(stop.get("lap")),
        "duration_ms": parse_duration_ms(stop.get("duration")),
    } for stop in pit_stops]

    return {
        "year": year,
        "round": round_number,
        "race_name": race_name,
        "total_laps": total_laps,
        "drivers": drivers,
        "positions": positions,
        "lap_times_ms": lap_times,
        "pit_stops": stops,
        "pit_data_available": bool(stops),
        "laps_available": laps_available,
        "duration_note": PIT_DURATION_NOTE,
    }


async def _empty() -> list:
    return []


@app.get("/race-analysis/{year}/{round}", response_model=RaceAnalysisResponse)
@limiter.limit("30/minute")
async def get_race_analysis(request: Request, year: int, round: int):
    cache_key = f"race_analysis_{year}_{round}"
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    # UpstreamError from any Jolpica call propagates -> 502 via the global handler.
    schedule = await jolpica.get_schedule(year)
    race = next((r for r in schedule if int(r.get("round", 0)) == round), None)
    if race is None:
        raise HTTPException(status_code=404, detail=f"No race {year} round {round}")
    race_dt = race_datetime(race)
    now = datetime.now(UTC)
    if race_dt is not None and race_dt > now:
        raise HTTPException(status_code=404, detail=f"{year} round {round} hasn't happened yet")

    laps_fetched = year >= LAPS_FIRST_SEASON
    results, laps, pit_stops = await asyncio.gather(
        fetch_results(year, round, "Race"),
        jolpica.get_race_laps(year, round) if laps_fetched else _empty(),
        jolpica.get_race_pit_stops(year, round) if year >= PIT_STOPS_FIRST_SEASON else _empty(),
    )

    analysis = build_race_analysis(year, round, race.get("race_name"),
                                   results, laps, pit_stops, laps_fetched)
    # Empty results mean the data isn't published yet: serve, but don't cache.
    if results:
        if year >= now.year and race_dt is not None and now - race_dt < RESULTS_VOLATILE_WINDOW:
            cache_set(cache_key, analysis, RESULTS_VOLATILE_MAX_AGE.total_seconds())
        else:
            cache_set_historical(cache_key, analysis)
    return analysis


# ── Circuit pit history ───────────────────────────────────────────────────────

def summarize_pit_race(season: int, round_number: int, race_name: str | None,
                       results: list, pit_stops: list) -> dict:
    finishers = {r.get("Driver", {}).get("driverId") for r in results
                 if is_finisher(r.get("status"))}
    finishers.discard(None)
    names = {r.get("Driver", {}).get("driverId"): _driver_name(r.get("Driver", {}))
             for r in results}

    finisher_stops = sum(1 for s in pit_stops if s.get("driverId") in finishers)
    avg = None
    if pit_stops and finishers:
        avg = round(finisher_stops / len(finishers), 2)

    fastest = None
    timed = [(parse_duration_ms(s.get("duration")), s) for s in pit_stops]
    timed = [(ms, s) for ms, s in timed if ms is not None]
    if timed:
        ms, stop = min(timed, key=lambda t: t[0])
        driver_id = stop.get("driverId", "")
        fastest = {"driver_id": driver_id, "name": names.get(driver_id) or driver_id,
                   "duration_ms": ms, "lap": _to_int(stop.get("lap"))}

    return {"season": season, "round": round_number, "race_name": race_name,
            "avg_stops_per_finisher": avg, "total_stops": len(pit_stops),
            "fastest_stop": fastest}


async def _pit_race(season: int, round_number: int, race_name: str | None) -> dict:
    results, pit_stops = await asyncio.gather(
        jolpica.get_race_results(season, round_number),
        jolpica.get_race_pit_stops(season, round_number),
    )
    return summarize_pit_race(season, round_number, race_name, results, pit_stops)


@app.get("/circuit/{circuit_id}/pit-history", response_model=CircuitPitHistoryResponse)
@limiter.limit("30/minute")
async def get_circuit_pit_history(
    request: Request,
    circuit_id: str,
    seasons: int = Query(default=10, ge=1, le=PIT_HISTORY_MAX_SEASONS),
):
    cache_key = f"circuit_pit_history:{circuit_id}:{seasons}"
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    winners = await jolpica.get_circuit_winners(circuit_id)
    if not winners:
        raise HTTPException(status_code=404, detail=f"No races found for circuit '{circuit_id}'")

    races = [(season, rnd, race) for (season, rnd), race in _unique_races(winners).items()
             if season >= PIT_STOPS_FIRST_SEASON]
    recent = sorted(races, key=lambda r: (r[0], r[1]))[-seasons:]
    summaries = await asyncio.gather(
        *(_pit_race(season, rnd, race.get("raceName")) for season, rnd, race in recent)
    )

    response = {"circuit_id": circuit_id, "races": list(summaries),
                "duration_note": PIT_DURATION_NOTE}
    if recent and recent[-1][0] == datetime.now(UTC).year:
        cache_set(cache_key, response, CIRCUIT_STATS_TTL_ACTIVE)
    else:
        cache_set_historical(cache_key, response)
    return response


# ── Weather forecast (Open-Meteo) ─────────────────────────────────────────────

def _session_start(session: dict) -> datetime:
    dt = datetime.fromisoformat(session["datetime"])
    return dt if dt.tzinfo else dt.replace(tzinfo=UTC)


@app.get("/weather/{year}/{round}", response_model=WeatherForecastResponse)
@limiter.limit("30/minute")
async def get_weather_forecast(request: Request, year: int, round: int):
    """
    Forecast for a race weekend at the circuit. Open-Meteo only forecasts ~16 days
    ahead, so a weekend further out returns available=false and the date to retry from.
    """
    cache_key = f"weather_{year}_{round}"
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    schedule = await jolpica.get_schedule(year)
    race = next((r for r in schedule if int(r.get("round", 0)) == round), None)
    if not race:
        raise HTTPException(status_code=404, detail="Round not found")

    sessions = [s for s in race.get("sessions", []) if s.get("datetime")]
    race_dt = race_datetime(race)
    if not sessions or race_dt is None:
        raise HTTPException(status_code=404, detail="Race date not found")

    now = datetime.now(UTC)
    if race_dt.date() < now.date():
        raise HTTPException(status_code=404, detail="Race is in the past")

    response = {
        "year": year,
        "round": round,
        "race_name": race.get("race_name"),
        "available": False,
        "available_from": None,
        "days": [],
        "sessions": [],
        "attribution": WEATHER_ATTRIBUTION,
    }
    first_start = min(_session_start(s) for s in sessions)
    if first_start - now > timedelta(days=FORECAST_HORIZON_DAYS):
        available_from = first_start.date() - timedelta(days=FORECAST_HORIZON_DAYS)
        response["available_from"] = available_from.isoformat()
        cache_set(cache_key, response, CACHE_TTL_WEATHER)
        return response

    if race.get("lat") is None or race.get("lng") is None:
        raise HTTPException(status_code=404, detail="Circuit location not found")

    # A weekend 15 days out runs past Open-Meteo's horizon; the later days come back
    # as the forecast extends (sessions beyond it keep null values until then).
    forecast = await openmeteo.get_forecast(
        race["lat"], race["lng"],
        start=first_start.date(),
        end=forecast_end_date(race_dt.date(), now.date()),
    )
    response.update({
        "available": True,
        "days": map_days(forecast),
        "sessions": map_sessions(forecast, sessions),
    })
    cache_set(cache_key, response, CACHE_TTL_WEATHER)
    return response


# ── News headlines ────────────────────────────────────────────────────────────

@app.get("/news", response_model=NewsResponse)
@limiter.limit("30/minute")
async def get_news(request: Request, limit: int = Query(default=20, ge=1, le=NEWS_MAX_ITEMS)):
    cache_key = "news"
    cached = cache_get(cache_key)
    if cached is None:
        # UpstreamError("news") when every feed fails -> 502
        items, failed_sources = await news.get_headlines()
        cached = {"items": items, "failed_sources": failed_sources}
        cache_set(cache_key, cached, CACHE_TTL_NEWS)
    return {"items": cached["items"][:limit], "failed_sources": cached["failed_sources"]}


# ── F1 history ────────────────────────────────────────────────────────────────

async def _season_winners(year: int) -> list[dict]:
    """Winners of one season from Jolpica (1h cache for the current season)."""
    cache_key = f"history_winners_{year}"
    cached = cache_get(cache_key)
    if cached is not None:
        return cached
    winners = [w for race in await jolpica.get_season_winners(year)
               for w in history.race_to_winners(race)]
    if year < datetime.now(UTC).year:
        cache_set_historical(cache_key, winners)
    else:
        cache_set(cache_key, winners, CACHE_TTL_HISTORY)
    return winners


async def all_race_winners() -> list[dict]:
    """
    The bundled winners file plus every season after the newest one in it
    (normally just the current season). A failed fetch leaves that season out
    rather than failing the request: the bundled history is still worth serving.
    """
    bundled = history.load_race_winners()
    current_year = datetime.now(UTC).year
    newest_bundled = max((w["season"] for w in bundled), default=current_year - 1)
    winners = list(bundled)
    for year in range(newest_bundled + 1, current_year + 1):
        try:
            winners.extend(await _season_winners(year))
        except UpstreamError as e:
            logger.warning(f"History: {year} winners unavailable, serving without them: {e}")
    return winners


@app.get("/history/on-this-day", response_model=OnThisDayResponse)
@limiter.limit("30/minute")
async def get_on_this_day(
    request: Request,
    on_date: Annotated[date | None, Query(alias="date")] = None,
    window: int = Query(default=3, ge=0, le=history.MAX_WINDOW),
):
    """Race winners whose race month-day is within ±window days of date (default today, UTC)."""
    target = on_date or datetime.now(UTC).date()
    items = history.on_this_day(await all_race_winners(), target, window)
    return {"date": target.isoformat(), "window": window, "items": items}


@app.get("/track-map/{circuit_id}", response_model=TrackMapResponse)
@limiter.limit("30/minute")
async def get_track_map(request: Request, circuit_id: str):
    """Generated circuit outline with sector breaks and corners (static, see track_maps.py)."""
    track = track_maps.get_track_map(circuit_id)
    if track is None:
        raise HTTPException(status_code=404, detail=f"No track map for circuit '{circuit_id}'")
    return track
