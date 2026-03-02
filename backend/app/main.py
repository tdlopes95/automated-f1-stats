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

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

from .database import Database
from .jolpica_client import JolpicaClient
from .openf1_client import OpenF1Client
from .scheduler import F1Scheduler

# Simple in-memory cache: key → (data, timestamp)
_cache: dict = {}
CACHE_TTL = 300  # 5 minutes

def cache_get(key: str):
    if key in _cache:
        data, ts = _cache[key]
        if time.time() - ts < CACHE_TTL:
            return data
    return None

def cache_set(key: str, data):
    _cache[key] = (data, time.time())
# ── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s"
)
logger = logging.getLogger(__name__)

# ── Globals (set during startup) ─────────────────────────────────────────────
db: Database = None
jolpica: JolpicaClient = None
openf1: OpenF1Client = None
scheduler: F1Scheduler = None

# OpenF1 session key cache: stores the active session_key for live polling
_active_session_key: Optional[int] = None


# ── Scheduler callbacks ───────────────────────────────────────────────────────

async def on_live_poll(session_name: str, race_name: str):
    """Called every 15s during an active session."""
    global _active_session_key
    try:
        # Resolve session key from OpenF1 if we don't have it
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
    """Called after a session ends — fetches and stores final results."""
    global _active_session_key
    try:
        year = datetime.utcnow().year

        if session_name == "Race":
            results = await jolpica.get_race_results(year, round_number)
            await db.save_results(year, round_number, "Race", results)
            # Also refresh standings after a race
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

        # Reset active session key after session ends
        _active_session_key = None
        logger.info(f"[RESULTS] {session_name} results saved for {race_name} R{round_number}")

    except Exception as e:
        logger.error(f"on_session_ended error: {e}")


# ── App Lifespan ──────────────────────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    global db, jolpica, openf1, scheduler

    # Init clients
    db = Database()
    await db.connect()

    jolpica = JolpicaClient()
    openf1 = OpenF1Client(
        access_token=os.getenv("OPENF1_TOKEN")  # None = free tier
    )

    # Start scheduler
    scheduler = F1Scheduler(
        jolpica=jolpica,
        on_live_poll=on_live_poll,
        on_session_ended=on_session_ended,
    )
    await scheduler.start()

    logger.info("F1 Backend started and ready.")
    yield

    # Cleanup
    scheduler.stop()
    await jolpica.close()
    await openf1.close()
    await db.close()
    logger.info("F1 Backend shut down.")


# ── FastAPI App ────────────────────────────────────────────────────────────────

app = FastAPI(
    title="F1 Backend API",
    description="Personal F1 data backend — race results, quali, sprint, live timing",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],      # Restrict this in production
    allow_methods=["GET"],
    allow_headers=["*"],
)


# ── Routes ────────────────────────────────────────────────────────────────────

@app.get("/")
async def root():
    return {"status": "ok", "service": "F1 Backend API"}


# ── Schedule ──────────────────────────────────────────────────────────────────

@app.get("/schedule")
async def get_schedule(year: Optional[int] = None):
    """Full race calendar for a season."""
    return await jolpica.get_schedule(year)


@app.get("/schedule/next")
async def get_next_race():
    """Next upcoming race weekend."""
    race = await jolpica.get_next_race()
    if not race:
        raise HTTPException(status_code=404, detail="No upcoming race found")
    return race


@app.get("/schedule/upcoming-sessions")
async def get_upcoming_sessions(days: int = Query(default=14, le=30)):
    """All sessions in the next N days — useful for the app's home screen."""
    return await jolpica.get_upcoming_sessions(days_ahead=days)


# ── Live Session ──────────────────────────────────────────────────────────────

@app.get("/live")
async def get_live_session():
    """
    Returns the latest live snapshot.
    During a session: near-real-time data (15s delay from our polling).
    Between sessions: last saved snapshot.
    """
    global _active_session_key

    # Try to find an active session
    if _active_session_key is None:
        session = await openf1.get_latest_session()
        if session:
            _active_session_key = session.get("session_key")

    if not _active_session_key:
        raise HTTPException(status_code=404, detail="No active session found")

    snapshot = await db.get_latest_snapshot(_active_session_key)
    if not snapshot:
        # Nothing cached yet — fetch live right now
        snapshot = await openf1.get_live_snapshot(_active_session_key)
        await db.save_snapshot(_active_session_key, snapshot)

    return snapshot


@app.get("/live/{session_key}")
async def get_live_by_session(session_key: int):
    """Get live/cached snapshot for a specific session key."""
    snapshot = await db.get_latest_snapshot(session_key)
    if not snapshot:
        snapshot = await openf1.get_live_snapshot(session_key)
        if not snapshot:
            raise HTTPException(status_code=404, detail="Session not found")
        await db.save_snapshot(session_key, snapshot)
    return snapshot


# ── Results ───────────────────────────────────────────────────────────────────

@app.get("/results/latest")
async def get_latest_results(
    session_type: str = Query(default="Race", enum=["Race", "Qualifying", "Sprint"]),
    year: Optional[int] = None
):
    """Latest stored results for a given session type."""
    current_year = datetime.utcnow().year

    # If year specified, fetch directly
    if year and year != current_year:
        results = await jolpica.get_race_results(year, None)
        return {"source": "live", "results": results}

    # Try current year first
    results = await db.get_latest_results(session_type)
    if results:
        return results

    # Current year has no results yet — fall back to last year's final race
    try:
        last_year = current_year - 1
        # Get last round of last year
        schedule = await jolpica.get_schedule(last_year)
        if schedule:
            last_round = max(r["round"] for r in schedule)
            fallback = await jolpica.get_race_results(last_year, last_round)
            if fallback:
                return {
                    "source": "fallback",
                    "year": last_year,
                    "round": last_round,
                    "results": fallback
                }
    except Exception as e:
        logger.error(f"Fallback results error: {e}")

    raise HTTPException(status_code=404, detail="No results found")


@app.get("/results/{year}/{round}")
async def get_results(
    year: int,
    round: int,
    session_type: str = Query(default="Race", enum=["Race", "Qualifying", "Sprint"])
):
    """Results for a specific round."""
    cached = await db.get_results(year, round, session_type)
    if cached:
        return {"source": "cache", "year": year, "round": round, "session_type": session_type, "results": cached}

    # Fetch from Jolpica
    if session_type == "Race":
        results = await jolpica.get_race_results(year, round)
    elif session_type == "Qualifying":
        results = await jolpica.get_qualifying_results(year, round)
    else:
        results = await jolpica.get_sprint_results(year, round)

    if results:
        await db.save_results(year, round, session_type, results)

    return {"source": "live", "year": year, "round": round, "session_type": session_type, "results": results}


# ── Standings ─────────────────────────────────────────────────────────────────

@app.get("/standings/drivers")
async def get_driver_standings(year: Optional[int] = None):
    target_year = year or datetime.utcnow().year
    # Always fetch from Jolpica when a specific year is requested
    # Only use cache for current year
    if year is None or year == datetime.utcnow().year:
        cached = await db.get_latest_driver_standings()
        if cached:
            return {"source": "cache", "standings": cached}
    standings = await jolpica.get_driver_standings(target_year)
    if standings:
        await db.save_driver_standings(target_year, None, standings)
    return {"source": "live", "standings": standings}


@app.get("/standings/constructors")
async def get_constructor_standings(year: Optional[int] = None):
    target_year = year or datetime.utcnow().year
    if year is None or year == datetime.utcnow().year:
        cached = await db.get_latest_constructor_standings()
        if cached:
            return {"source": "cache", "standings": cached}
    standings = await jolpica.get_constructor_standings(target_year)
    if standings:
        await db.save_constructor_standings(target_year, None, standings)
    return {"source": "live", "standings": standings}


# ── Session Details (OpenF1) ──────────────────────────────────────────────────

@app.get("/sessions")
async def get_sessions(year: Optional[int] = None, session_type: Optional[str] = None):
    """All OpenF1 sessions, optionally filtered."""
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
async def get_pit_stops(session_key: int):
    cache_key = f"pit_stops_{session_key}"
    cached = cache_get(cache_key)
    if cached:
        return cached

    pit_stops_raw = await openf1.get_pit_stops(session_key)
    drivers_raw   = await openf1.get_drivers(session_key)
    # Get pit stops and drivers in parallel
    pit_stops_raw = await openf1.get_pit_stops(session_key)
    drivers_raw   = await openf1.get_drivers(session_key)

    # Build driver lookup: number → {name, team, colour}
    driver_lookup = {}
    for d in drivers_raw:
        driver_lookup[d["driver_number"]] = {
            "name":   d.get("full_name", ""),
            "team":   d.get("team_name", ""),
            "colour": "#" + d.get("team_colour", "FFFFFF")
        }

    # Filter out formation/installation laps (stop_duration is None)
    # Real pit stops always have a stop_duration value
    real_stops = [p for p in pit_stops_raw if p.get("stop_duration") is not None]

    # Keep only fastest stop per driver
    fastest = {}
    for stop in real_stops:
        num = stop["driver_number"]
        duration = stop.get("stop_duration", 999)
        if num not in fastest or duration < fastest[num]["stop_duration"]:
            fastest[num] = stop

    # Enrich with driver info and sort by fastest time
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
async def get_session_key(year: int, round: int):
    cache_key = f"session_key_{year}_{round}"
    cached = cache_get(cache_key)
    if cached:
        return cached
    """Get OpenF1 session key for a specific race round."""
    # Get race date from Jolpica
    schedule = await jolpica.get_schedule(year)
    race = next((r for r in schedule if r["round"] == round), None)
    if not race:
        raise HTTPException(status_code=404, detail="Round not found")

    # Find race session datetime
    race_date = None
    for session in race.get("sessions", []):
        if session["name"] == "Race":
            race_date = session["datetime"][:10]  # just the date part YYYY-MM-DD
            break

    if not race_date:
        raise HTTPException(status_code=404, detail="Race date not found")

    # Find matching OpenF1 session by date
    sessions = await openf1.get_sessions(year=year, session_type="Race")
    for session in sessions:
        if session.get("date_start", "").startswith(race_date):
            return {"session_key": session["session_key"]}

    result = {"session_key": session["session_key"]}
    cache_set(cache_key, result)
    return result
    raise HTTPException(status_code=404, detail="Session key not found")
    