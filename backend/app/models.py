"""
F1 Backend - Data Models
Pydantic models for API responses
"""

from pydantic import BaseModel
from typing import Optional
from datetime import datetime


# ── Session / Meeting ──────────────────────────────────────────────────────────

class Session(BaseModel):
    session_key: int
    session_name: str          # "Race", "Qualifying", "Sprint", etc.
    session_type: str
    date_start: datetime
    date_end: Optional[datetime] = None
    gmt_offset: Optional[str] = None
    location: Optional[str] = None
    country_name: Optional[str] = None
    circuit_short_name: Optional[str] = None
    year: Optional[int] = None


class Meeting(BaseModel):
    meeting_key: int
    meeting_name: str
    meeting_official_name: Optional[str] = None
    location: Optional[str] = None
    country_name: Optional[str] = None
    date_start: Optional[datetime] = None
    year: Optional[int] = None


# ── Drivers ────────────────────────────────────────────────────────────────────

class Driver(BaseModel):
    driver_number: int
    full_name: Optional[str] = None
    name_acronym: Optional[str] = None       # e.g. "VER", "HAM"
    team_name: Optional[str] = None
    team_colour: Optional[str] = None        # hex color, e.g. "3671C6"
    country_code: Optional[str] = None
    headshot_url: Optional[str] = None
    session_key: Optional[int] = None


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
    lap_duration: Optional[float] = None     # seconds
    duration_sector_1: Optional[float] = None
    duration_sector_2: Optional[float] = None
    duration_sector_3: Optional[float] = None
    i1_speed: Optional[int] = None           # km/h
    i2_speed: Optional[int] = None
    st_speed: Optional[int] = None           # speed trap
    is_pit_out_lap: Optional[bool] = None
    date_start: Optional[datetime] = None


# ── Pit Stops ──────────────────────────────────────────────────────────────────

class PitStop(BaseModel):
    session_key: int
    meeting_key: int
    driver_number: int
    lap_number: int
    date: Optional[datetime] = None
    pit_duration: Optional[float] = None     # total pit lane time (s)
    stop_duration: Optional[float] = None    # stationary time (s)


# ── Stints / Tyres ────────────────────────────────────────────────────────────

class Stint(BaseModel):
    session_key: int
    meeting_key: int
    driver_number: int
    stint_number: int
    lap_start: int
    lap_end: Optional[int] = None
    compound: Optional[str] = None           # "SOFT", "MEDIUM", "HARD", "INTER", "WET"
    tyre_age_at_start: Optional[int] = None


# ── Race Control ───────────────────────────────────────────────────────────────

class RaceControlMessage(BaseModel):
    session_key: int
    meeting_key: int
    date: datetime
    category: Optional[str] = None          # "Flag", "SafetyCar", "Drs", etc.
    flag: Optional[str] = None              # "GREEN", "YELLOW", "RED", "SC", "VSC"
    scope: Optional[str] = None             # "Track", "Sector", "Driver"
    sector: Optional[int] = None
    driver_number: Optional[int] = None
    message: Optional[str] = None


# ── Weather ────────────────────────────────────────────────────────────────────

class Weather(BaseModel):
    session_key: int
    meeting_key: int
    date: datetime
    air_temperature: Optional[float] = None
    track_temperature: Optional[float] = None
    humidity: Optional[float] = None
    pressure: Optional[float] = None
    rainfall: Optional[bool] = None
    wind_speed: Optional[float] = None
    wind_direction: Optional[int] = None


# ── Intervals (gaps between drivers) ──────────────────────────────────────────

class Interval(BaseModel):
    session_key: int
    meeting_key: int
    driver_number: int
    date: datetime
    gap_to_leader: Optional[str] = None    # e.g. "+5.234" or "1 LAP"
    interval: Optional[str] = None         # gap to car ahead


# ── Composite: Live Race State ─────────────────────────────────────────────────

class LiveDriverState(BaseModel):
    """Aggregated live state for a single driver — sent to the Android app"""
    driver_number: int
    name_acronym: Optional[str] = None
    full_name: Optional[str] = None
    team_name: Optional[str] = None
    team_colour: Optional[str] = None
    position: Optional[int] = None
    gap_to_leader: Optional[str] = None
    interval: Optional[str] = None
    last_lap_duration: Optional[float] = None
    current_compound: Optional[str] = None
    tyre_age: Optional[int] = None
    pit_stops: int = 0
    last_updated: Optional[datetime] = None


class LiveSessionState(BaseModel):
    """Full live state snapshot sent to the Android app"""
    session_key: int
    session_name: str
    session_type: str
    is_live: bool
    latest_flag: Optional[str] = None       # current track flag
    safety_car_active: bool = False
    vsc_active: bool = False
    drivers: list[LiveDriverState] = []
    weather: Optional[Weather] = None
    last_updated: datetime = datetime.utcnow()