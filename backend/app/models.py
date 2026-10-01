"""
F1 Backend - Data Models
Pydantic models for API responses
"""

from datetime import UTC, datetime
from typing import Any

from pydantic import BaseModel

# ── Session / Meeting ──────────────────────────────────────────────────────────

class Session(BaseModel):
    session_key: int
    session_name: str          # "Race", "Qualifying", "Sprint", etc.
    session_type: str
    date_start: datetime
    date_end: datetime | None = None
    gmt_offset: str | None = None
    location: str | None = None
    country_name: str | None = None
    circuit_short_name: str | None = None
    year: int | None = None


class Meeting(BaseModel):
    meeting_key: int
    meeting_name: str
    meeting_official_name: str | None = None
    location: str | None = None
    country_name: str | None = None
    date_start: datetime | None = None
    year: int | None = None


# ── Drivers ────────────────────────────────────────────────────────────────────

class Driver(BaseModel):
    driver_number: int
    full_name: str | None = None
    name_acronym: str | None = None       # e.g. "VER", "HAM"
    team_name: str | None = None
    team_colour: str | None = None        # hex color, e.g. "3671C6"
    country_code: str | None = None
    headshot_url: str | None = None
    session_key: int | None = None


# ── Race Position ──────────────────────────────────────────────────────────────

class Position(BaseModel):
    session_key: int
    meeting_key: int
    driver_number: int
    date: datetime
    position: int


# ── Lap Data ───────────────────────────────────────────────────────────────────

class Lap(BaseModel):
    session_key: int
    meeting_key: int
    driver_number: int
    lap_number: int
    lap_duration: float | None = None     # seconds
    duration_sector_1: float | None = None
    duration_sector_2: float | None = None
    duration_sector_3: float | None = None
    i1_speed: int | None = None           # km/h
    i2_speed: int | None = None
    st_speed: int | None = None           # speed trap
    is_pit_out_lap: bool | None = None
    date_start: datetime | None = None


# ── Pit Stops ──────────────────────────────────────────────────────────────────

class PitStop(BaseModel):
    session_key: int
    meeting_key: int
    driver_number: int
    lap_number: int
    date: datetime | None = None
    pit_duration: float | None = None     # total pit lane time (s)
    stop_duration: float | None = None    # stationary time (s)


# ── Stints / Tyres ────────────────────────────────────────────────────────────

class Stint(BaseModel):
    session_key: int
    meeting_key: int
    driver_number: int
    stint_number: int
    lap_start: int
    lap_end: int | None = None
    compound: str | None = None           # "SOFT", "MEDIUM", "HARD", "INTER", "WET"
    tyre_age_at_start: int | None = None


# ── Race Control ───────────────────────────────────────────────────────────────

class RaceControlMessage(BaseModel):
    session_key: int
    meeting_key: int
    date: datetime
    category: str | None = None          # "Flag", "SafetyCar", "Drs", etc.
    flag: str | None = None              # "GREEN", "YELLOW", "RED", "SC", "VSC"
    scope: str | None = None             # "Track", "Sector", "Driver"
    sector: int | None = None
    driver_number: int | None = None
    message: str | None = None


# ── Weather ────────────────────────────────────────────────────────────────────

class Weather(BaseModel):
    session_key: int
    meeting_key: int
    date: datetime
    air_temperature: float | None = None
    track_temperature: float | None = None
    humidity: float | None = None
    pressure: float | None = None
    rainfall: bool | None = None
    wind_speed: float | None = None
    wind_direction: int | None = None


# ── Intervals (gaps between drivers) ──────────────────────────────────────────

class Interval(BaseModel):
    session_key: int
    meeting_key: int
    driver_number: int
    date: datetime
    gap_to_leader: str | None = None    # e.g. "+5.234" or "1 LAP"
    interval: str | None = None         # gap to car ahead


# ── Composite: Live Race State ─────────────────────────────────────────────────

class LiveDriverState(BaseModel):
    """Aggregated live state for a single driver — sent to the Android app"""
    driver_number: int
    name_acronym: str | None = None
    full_name: str | None = None
    team_name: str | None = None
    team_colour: str | None = None
    position: int | None = None
    gap_to_leader: str | None = None
    interval: str | None = None
    last_lap_duration: float | None = None
    current_compound: str | None = None
    tyre_age: int | None = None
    pit_stops: int = 0
    last_updated: datetime | None = None


class LiveSessionState(BaseModel):
    """Full live state snapshot sent to the Android app"""
    session_key: int
    session_name: str
    session_type: str
    is_live: bool
    latest_flag: str | None = None       # current track flag
    safety_car_active: bool = False
    vsc_active: bool = False
    drivers: list[LiveDriverState] = []
    weather: Weather | None = None
    last_updated: datetime = datetime.now(UTC)


# ── Schedule ───────────────────────────────────────────────────────────────────

class SessionEntry(BaseModel):
    name: str
    datetime: str


class RaceSchedule(BaseModel):
    round: int
    race_name: str | None = None
    circuit: str | None = None
    circuit_id: str | None = None
    country: str | None = None
    locality: str | None = None
    sessions: list[SessionEntry] = []


# ── Results response wrapper ───────────────────────────────────────────────────

class ResultsResponse(BaseModel):
    source: str
    year: int
    round: int
    session_type: str | None = None
    race_name: str | None = None
    results: list[Any] = []


# ── Standings response wrappers ────────────────────────────────────────────────

class DriverStandingsResponse(BaseModel):
    source: str
    season_started: bool | None = None
    year: int | None = None
    standings: list[Any] = []


class ConstructorStandingsResponse(BaseModel):
    source: str
    year: int | None = None
    standings: list[Any] = []


# ── Meeting info (endpoint shape) ──────────────────────────────────────────────

class MeetingInfo(BaseModel):
    meeting_key: int | None = None
    meeting_name: str | None = None
    location: str | None = None
    country_name: str | None = None
    country_flag: str | None = None
    circuit_short_name: str | None = None
    circuit_type: str | None = None
    circuit_image: str | None = None
    gmt_offset: str | None = None
    date_start: str | None = None
    year: int | None = None


# ── Driver info (endpoint shape) ───────────────────────────────────────────────

class DriverInfo(BaseModel):
    driver_number: int | None = None
    name_acronym: str | None = None
    full_name: str | None = None
    headshot_url: str | None = None
    team_name: str | None = None
    team_colour: str | None = None
    country_code: str | None = None


# ── Circuit Stats ──────────────────────────────────────────────────────────────

class DriverStat(BaseModel):
    driverId: str
    name: str
    count: int
    years: list[int] | None = None


class ConstructorStat(BaseModel):
    constructorId: str
    name: str
    count: int


class LapRecord(BaseModel):
    driverId: str
    name: str
    time: str
    year: int


class CircuitStatsResponse(BaseModel):
    circuitId: str
    circuitName: str
    locality: str
    country: str
    totalRaces: int
    firstGPYear: int
    lastGPYear: int
    mostWins: DriverStat | None = None
    mostPoles: DriverStat | None = None
    mostConstructorWins: ConstructorStat | None = None
    lapRecord: LapRecord | None = None
    lapRecordSinceYear: int | None = None   # earliest season with a timed fastest lap
    dataNote: str | None = None