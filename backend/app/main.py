"""
F1 Backend - FastAPI Main Application
Run with: uvicorn app.main:app --reload --port 8000
"""

import logging
import os
import time
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Optional

from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware

from .database import Database
from .jolpica_client import JolpicaClient
from .openf1_client import OpenF1Client
from .scheduler import F1Scheduler

from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded

limiter = Limiter(key_func=get_remote_address)

# ── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s"
)
logger = logging.getLogger(__name__)

# ── In-memory cache ───────────────────────────────────────────────────────────
_cache: dict = {}
CACHE_TTL         = 300        # 5 minutes  — live/current data
CACHE_TTL_FOREVER = 86400 * 7  # 7 days     — historical data (never changes)

def cache_get(key: str):
    if key in _cache:
        entry = _cache[key]
        ttl  = entry[2] if len(entry) == 3 else CACHE_TTL
        data, ts = entry[0], entry[1]
        if time.time() - ts < ttl:
            return data
        del _cache[key]
    return None

def cache_set(key: str, data):
    _cache[key] = (data, time.time())

def cache_set_historical(key: str, data):
    _cache[key] = (data, time.time(), CACHE_TTL_FOREVER)

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

# ── Globals ───────────────────────────────────────────────────────────────────
db: Database        = None
jolpica: JolpicaClient  = None
openf1: OpenF1Client    = None
scheduler: F1Scheduler  = None
_active_session_key: Optional[int] = None

# ── Scheduler callbacks ───────────────────────────────────────────────────────

async def on_live_poll(session_name: str, race_name: str):
    global _active_session_key
    try:
        if _active_session_key is None:
            session = await openf1.get_latest_session()
            if session:
                _active_session_key = session.get("session_key")
                await db.upsert_session(session)
        if _active_session_key:
            snapshot = await openf1.get_live_snapshot(_active_session_key)
            snapshot["session_name"] = session_name
            snapshot["is_live"] = True
            await db.save_snapshot(_active_session_key, snapshot)
            logger.info(f"[LIVE] Snapshot saved for session {_active_session_key}")
    except Exception as e:
        logger.error(f"on_live_poll error: {e}")


async def on_session_ended(session_name: str, race_name: str, round_number: int):
    global _active_session_key
    try:
        year = datetime.utcnow().year
        if session_name == "Race":
            results = await jolpica.get_race_results(year, round_number)
            await db.save_results(year, round_number, "Race", results)
            standings = await jolpica.get_driver_standings(year)
            await db.save_driver_standings(year, round_number, standings)
            c_standings = await jolpica.get_constructor_standings(year)
            await db.save_constructor_standings(year, round_number, c_standings)
        elif session_name == "Qualifying":
            results = await jolpica.get_qualifying_results(year, round_number)
            await db.save_results(year, round_number, "Qualifying", results)
        elif session_name == "Sprint":
            results = await jolpica.get_sprint_results(year, round_number)
            await db.save_results(year, round_number, "Sprint", results)
        _active_session_key = None
        logger.info(f"[RESULTS] {session_name} results saved for {race_name} R{round_number}")
    except Exception as e:
        logger.error(f"on_session_ended error: {e}")


# ── App Lifespan ──────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    global db, jolpica, openf1, scheduler
    db = Database()
    await db.connect()
    jolpica  = JolpicaClient()
    openf1   = OpenF1Client(access_token=os.getenv("OPENF1_TOKEN"))
    scheduler = F1Scheduler(
        jolpica=jolpica,
        on_live_poll=on_live_poll,
        on_session_ended=on_session_ended,
    )
    await scheduler.start()
    logger.info("F1 Backend started and ready.")
    yield
    scheduler.stop()
    await jolpica.close()
    await openf1.close()
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

@app.get("/schedule")
@limiter.limit("30/minute")
async def get_schedule(request: Request, year: Optional[int] = None):
    cache_key = f"schedule_{year or 'current'}"
    cached = cache_get(cache_key)
    if cached:
        return cached
    result = await jolpica.get_schedule(year)
    if result:
        current_year = datetime.utcnow().year
        if year and year < current_year:
            cache_set_historical(cache_key, result)
        else:
            cache_set(cache_key, result)
    return result


@app.get("/schedule/next")
@limiter.limit("30/minute")
async def get_next_race(request: Request):
    race = await jolpica.get_next_race()
    if not race:
        raise HTTPException(status_code=404, detail="No upcoming race found")
    return race


@app.get("/schedule/upcoming-sessions")
async def get_upcoming_sessions(days: int = Query(default=14, le=30)):
    return await jolpica.get_upcoming_sessions(days_ahead=days)


# ── Live Session ──────────────────────────────────────────────────────────────

@app.get("/live")
@limiter.limit("60/minute")
async def get_live_session(request: Request):
    global _active_session_key
    if _active_session_key is None:
        session = await openf1.get_latest_session()
        if session:
            _active_session_key = session.get("session_key")
    if not _active_session_key:
        raise HTTPException(status_code=404, detail="No active session found")
    snapshot = await db.get_latest_snapshot(_active_session_key)
    if not snapshot:
        snapshot = await openf1.get_live_snapshot(_active_session_key)
        await db.save_snapshot(_active_session_key, snapshot)
    return snapshot


@app.get("/live/{session_key}")
@limiter.limit("60/minute")
async def get_live_by_session(request: Request, session_key: int):
    snapshot = await db.get_latest_snapshot(session_key)
    if not snapshot:
        snapshot = await openf1.get_live_snapshot(session_key)
        if not snapshot:
            raise HTTPException(status_code=404, detail="Session not found")
        await db.save_snapshot(session_key, snapshot)
    return snapshot


# ── Results ───────────────────────────────────────────────────────────────────

@app.get("/results/latest")
@limiter.limit("30/minute")
async def get_latest_results(
    request: Request,
    session_type: str = Query(default="Race", enum=["Race", "Qualifying", "Sprint"]),
    year: Optional[int] = None
):
    """Latest race winner — falls back to previous year if season hasn't started."""
    current_year = datetime.utcnow().year

    try:
        current_schedule = await jolpica.get_schedule(current_year)
        today = datetime.utcnow().date()
        current_year_has_results = False
        if current_schedule:
            for race in current_schedule:
                for session in race.get("sessions", []):
                    if session.get("name") == "Race":
                        dt_str = session.get("datetime", "")
                        if dt_str:
                            race_date = datetime.fromisoformat(dt_str[:10]).date()
                            if race_date <= today:
                                current_year_has_results = True
                                break
                if current_year_has_results:
                    break
    except Exception as e:
        logger.error(f"Schedule check error: {e}")
        current_year_has_results = False

    target_year = current_year if current_year_has_results else current_year - 1

    try:
        schedule = await jolpica.get_schedule(target_year)
        if not schedule:
            raise HTTPException(status_code=404, detail="No schedule found")

        today = datetime.utcnow().date()
        last_round = 0
        for race in schedule:
            for session in race.get("sessions", []):
                if session.get("name") == "Race":
                    dt_str = session.get("datetime", "")
                    if dt_str:
                        race_date = datetime.fromisoformat(dt_str[:10]).date()
                        if race_date <= today:
                            rnd = race.get("round")
                            if rnd and int(rnd) > last_round:
                                last_round = int(rnd)

        if last_round == 0:
            last_round = max(r["round"] for r in schedule)

        race_name = ""
        for race in schedule:
            if int(race.get("round", 0)) == last_round:
                race_name = race.get("race_name", "")
                break

        results = await jolpica.get_race_results(target_year, last_round)
        if results:
            return {"source": "live", "year": target_year,
                    "round": last_round, "race_name": race_name, "results": results}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Latest results error: {e}")

    raise HTTPException(status_code=404, detail="No results found")


@app.get("/results/{year}/{round}")
@limiter.limit("60/minute")
async def get_results(
    request: Request,
    year: int,
    round: int,
    session_type: str = Query(default="Race", enum=["Race", "Qualifying", "Sprint"])
):
    cached = await db.get_results(year, round, session_type)
    if cached:
        return {"source": "cache", "year": year, "round": round,
                "session_type": session_type, "results": cached}

    current_year = datetime.utcnow().year
    if year < current_year:
        cache_key = f"results_{year}_{round}_{session_type}"
        mem_cached = cache_get(cache_key)
        if mem_cached:
            return mem_cached

    if session_type == "Race":
        results = await jolpica.get_race_results(year, round)
    elif session_type == "Qualifying":
        results = await jolpica.get_qualifying_results(year, round)
    else:
        results = await jolpica.get_sprint_results(year, round)

    if results:
        await db.save_results(year, round, session_type, results)
        if year < current_year:
            cache_set_historical(
                f"results_{year}_{round}_{session_type}",
                {"source": "cache", "year": year, "round": round,
                 "session_type": session_type, "results": results}
            )

    return {"source": "live", "year": year, "round": round,
            "session_type": session_type, "results": results}


# ── Standings ─────────────────────────────────────────────────────────────────

@app.get("/standings/drivers")
@limiter.limit("30/minute")
async def get_driver_standings(request: Request, year: Optional[int] = None):
    current_year = datetime.utcnow().year
    target_year  = year or current_year

    season_started = True
    if target_year == current_year:
        try:
            current_schedule = await jolpica.get_schedule(current_year)
            today = datetime.utcnow().date()
            season_started = False
            if current_schedule:
                for race in current_schedule:
                    for session in race.get("sessions", []):
                        if session.get("name") == "Race":
                            dt_str = session.get("datetime", "")
                            if dt_str:
                                race_date = datetime.fromisoformat(dt_str[:10]).date()
                                if race_date <= today:
                                    season_started = True
                                    break
                    if season_started:
                        break
        except Exception as e:
            logger.error(f"Season check error: {e}")
            season_started = True

    if target_year != current_year:
        cache_key = f"driver_standings_{target_year}"
        cached = cache_get(cache_key)
        if cached:
            return cached

    if target_year == current_year:
        cached = await db.get_latest_driver_standings()
        if cached and season_started:
            return {"source": "cache", "season_started": True, "standings": cached}

    standings = await jolpica.get_driver_standings(target_year)

    if not standings and target_year == current_year:
        standings = await jolpica.get_driver_standings(current_year - 1)
        standings = add_gap_to_second(standings)
        return {"source": "fallback", "year": current_year - 1,
                "season_started": False, "standings": standings}

    standings = add_gap_to_second(standings)

    if standings:
        if target_year == current_year:
            await db.save_driver_standings(target_year, None, standings)
        else:
            cache_set_historical(
                f"driver_standings_{target_year}",
                {"source": "cache", "season_started": True, "standings": standings}
            )

    return {"source": "live", "season_started": season_started, "standings": standings}


@app.get("/standings/constructors")
@limiter.limit("30/minute")
async def get_constructor_standings(request: Request, year: Optional[int] = None):
    current_year = datetime.utcnow().year
    target_year  = year or current_year

    if target_year == current_year:
        cached = await db.get_latest_constructor_standings()
        if cached:
            return {"source": "cache", "standings": cached}
    else:
        cache_key = f"constructor_standings_{target_year}"
        cached = cache_get(cache_key)
        if cached:
            return cached

    standings = await jolpica.get_constructor_standings(target_year)

    if not standings and target_year == current_year:
        standings = await jolpica.get_constructor_standings(current_year - 1)
        return {"source": "fallback", "year": current_year - 1, "standings": standings}

    if standings:
        if target_year == current_year:
            await db.save_constructor_standings(target_year, None, standings)
        else:
            cache_set_historical(
                f"constructor_standings_{target_year}",
                {"source": "cache", "standings": standings}
            )

    return {"source": "live", "standings": standings}


# ── Session Details (OpenF1) ──────────────────────────────────────────────────

@app.get("/sessions")
async def get_sessions(year: Optional[int] = None, session_type: Optional[str] = None):
    return await openf1.get_sessions(year=year, session_type=session_type)


@app.get("/sessions/{session_key}/laps")
async def get_session_laps(session_key: int, driver_number: Optional[int] = None):
    return await openf1.get_laps(session_key, driver_number=driver_number)


@app.get("/sessions/{session_key}/fastest-laps")
async def get_fastest_laps(session_key: int):
    return await openf1.get_fastest_laps(session_key)


@app.get("/sessions/{session_key}/stints")
async def get_stints(session_key: int, driver_number: Optional[int] = None):
    return await openf1.get_stints(session_key, driver_number=driver_number)


@app.get("/sessions/{session_key}/pit-stops")
@limiter.limit("30/minute")
async def get_pit_stops(request: Request, session_key: int):
    cache_key = f"pit_stops_{session_key}"
    cached = cache_get(cache_key)
    if cached:
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
            "pit_duration":  stop.get("pit_duration"),
        })

    result.sort(key=lambda x: x["stop_duration"])
    cache_set(cache_key, result)
    return result


@app.get("/sessions/{session_key}/race-control")
async def get_race_control(session_key: int):
    return await openf1.get_race_control(session_key)


@app.get("/sessions/{session_key}/weather")
async def get_weather(session_key: int):
    return await openf1.get_latest_weather(session_key)


@app.get("/sessions/{session_key}/drivers")
async def get_drivers(session_key: int):
    return await openf1.get_drivers(session_key)


@app.get("/session-key/{year}/{round}")
@limiter.limit("30/minute")
async def get_session_key(request: Request, year: int, round: int):
    cache_key = f"session_key_{year}_{round}"
    cached = cache_get(cache_key)
    if cached:
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