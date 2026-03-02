"""
F1 Backend - OpenF1 API Client
Wraps all calls to https://api.openf1.org/v1
Free tier: historical data (no auth needed)
Sponsor tier: live data during sessions (requires token - see auth setup)
"""

import asyncio
import logging
from datetime import datetime
from typing import Any, Optional

import httpx

logger = logging.getLogger(__name__)

BASE_URL = "https://api.openf1.org/v1"

# Rate limits (free tier: 3 req/s, 30 req/min)
# We stay well under by spacing calls
FREE_TIER_DELAY = 0.4   # ~2.5 req/s, safe margin


class OpenF1Client:
    def __init__(self, access_token: Optional[str] = None):
        """
        access_token: Only needed for live data (sponsor tier).
                      Leave None for historical/free tier.
        """
        self.access_token = access_token
        headers = {"Content-Type": "application/json"}
        if access_token:
            headers["Authorization"] = f"Bearer {access_token}"
        self._client = httpx.AsyncClient(
            base_url=BASE_URL,
            headers=headers,
            timeout=15.0
        )

    async def _get(self, endpoint: str, params: dict = None):
    for attempt in range(3):  # retry up to 3 times
        try:
            response = await self._client.get(
                f"{self.BASE_URL}{endpoint}",
                params=params
            )
            response.raise_for_status()
            return response.json()
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 429:
                wait = 2 ** attempt  # 1s, 2s, 4s backoff
                logger.warning(f"Rate limited, retrying in {wait}s...")
                await asyncio.sleep(wait)
            else:
                logger.error(f"OpenF1 HTTP error {e.response.status_code} on {endpoint}: {e}")
                return []
    return []

    # ── Sessions & Meetings ──────────────────────────────────────────────────

    async def get_sessions(self, year: Optional[int] = None, session_type: Optional[str] = None) -> list[dict]:
        """
        Get all sessions, optionally filtered.
        session_type: "Race", "Qualifying", "Sprint", "Sprint Qualifying",
                      "Practice 1", "Practice 2", "Practice 3"
        """
        params = {}
        if year:
            params["year"] = year
        if session_type:
            params["session_type"] = session_type
        return await self._get("sessions", params)

    async def get_latest_session(self, session_type: Optional[str] = None) -> Optional[dict]:
        """Get the most recent session (useful to find current/last race)."""
        params = {"session_key": "latest"}
        if session_type:
            params["session_type"] = session_type
        results = await self._get("sessions", params)
        return results[0] if results else None

    async def get_meetings(self, year: Optional[int] = None) -> list[dict]:
        """Get all race weekends (meetings) for a year."""
        params = {}
        if year:
            params["year"] = year
        return await self._get("meetings", params)

    # ── Drivers ──────────────────────────────────────────────────────────────

    async def get_drivers(self, session_key: int) -> list[dict]:
        """Get all drivers in a session."""
        return await self._get("drivers", {"session_key": session_key})

    async def get_driver(self, session_key: int, driver_number: int) -> Optional[dict]:
        results = await self._get("drivers", {
            "session_key": session_key,
            "driver_number": driver_number
        })
        return results[0] if results else None

    # ── Race Positions ────────────────────────────────────────────────────────

    async def get_positions(self, session_key: int, driver_number: Optional[int] = None) -> list[dict]:
        """
        Get position data for a session.
        During a live session, returns current positions.
        After session, returns full position history.
        """
        params = {"session_key": session_key}
        if driver_number:
            params["driver_number"] = driver_number
        return await self._get("position", params)

    async def get_latest_positions(self, session_key: int) -> list[dict]:
        """
        Get the most recent position for each driver.
        We fetch all positions and take the latest per driver.
        """
        all_positions = await self.get_positions(session_key)
        latest: dict[int, dict] = {}
        for pos in all_positions:
            driver = pos.get("driver_number")
            existing = latest.get(driver)
            if not existing or pos.get("date", "") > existing.get("date", ""):
                latest[driver] = pos
        return list(latest.values())

    # ── Laps ─────────────────────────────────────────────────────────────────

    async def get_laps(self, session_key: int, driver_number: Optional[int] = None,
                       lap_number: Optional[int] = None) -> list[dict]:
        params = {"session_key": session_key}
        if driver_number:
            params["driver_number"] = driver_number
        if lap_number:
            params["lap_number"] = lap_number
        return await self._get("laps", params)

    async def get_fastest_laps(self, session_key: int) -> list[dict]:
        """Get the fastest lap per driver in a session."""
        all_laps = await self.get_laps(session_key)
        fastest: dict[int, dict] = {}
        for lap in all_laps:
            if not lap.get("lap_duration"):
                continue
            driver = lap.get("driver_number")
            existing = fastest.get(driver)
            if not existing or lap["lap_duration"] < existing["lap_duration"]:
                fastest[driver] = lap
        return list(fastest.values())

    # ── Pit Stops ─────────────────────────────────────────────────────────────

    async def get_pit_stops(self, session_key: int, driver_number: Optional[int] = None) -> list[dict]:
        params = {"session_key": session_key}
        if driver_number:
            params["driver_number"] = driver_number
        return await self._get("pit", params)

    # ── Stints / Tyre Strategy ────────────────────────────────────────────────

    async def get_stints(self, session_key: int, driver_number: Optional[int] = None) -> list[dict]:
        params = {"session_key": session_key}
        if driver_number:
            params["driver_number"] = driver_number
        return await self._get("stints", params)

    # ── Race Control (flags, SC, VSC, DRS) ───────────────────────────────────

    async def get_race_control(self, session_key: int) -> list[dict]:
        return await self._get("race_control", {"session_key": session_key})

    async def get_current_flag(self, session_key: int) -> Optional[str]:
        """Returns the current/latest track flag."""
        messages = await self.get_race_control(session_key)
        flag_messages = [m for m in messages if m.get("flag")]
        if not flag_messages:
            return None
        latest = max(flag_messages, key=lambda m: m.get("date", ""))
        return latest.get("flag")

    # ── Intervals ─────────────────────────────────────────────────────────────

    async def get_intervals(self, session_key: int) -> list[dict]:
        """Get gaps between drivers. Available during/after race sessions."""
        return await self._get("intervals", {"session_key": session_key})

    async def get_latest_intervals(self, session_key: int) -> list[dict]:
        """Get most recent interval for each driver."""
        all_intervals = await self.get_intervals(session_key)
        latest: dict[int, dict] = {}
        for interval in all_intervals:
            driver = interval.get("driver_number")
            existing = latest.get(driver)
            if not existing or interval.get("date", "") > existing.get("date", ""):
                latest[driver] = interval
        return list(latest.values())

    # ── Weather ───────────────────────────────────────────────────────────────

    async def get_weather(self, session_key: int) -> list[dict]:
        return await self._get("weather", {"session_key": session_key})

    async def get_latest_weather(self, session_key: int) -> Optional[dict]:
        weather = await self.get_weather(session_key)
        if not weather:
            return None
        return max(weather, key=lambda w: w.get("date", ""))

    # ── Championship Standings ────────────────────────────────────────────────

    async def get_driver_standings(self, session_key: int) -> list[dict]:
        """Only available after race sessions."""
        return await self._get("championship_drivers", {"session_key": session_key})

    async def get_constructor_standings(self, session_key: int) -> list[dict]:
        """Only available after race sessions."""
        return await self._get("championship_teams", {"session_key": session_key})

    # ── Composite: Full Live Snapshot ─────────────────────────────────────────

    async def get_live_snapshot(self, session_key: int) -> dict[str, Any]:
        """
        Fetches everything needed to display a live race screen in one call.
        Returns a dict ready to be serialised into LiveSessionState.
        Use this during a live session (sponsor tier recommended for real-time).
        """
        # Run all requests concurrently for speed
        positions, intervals, stints, pit_stops, race_control, weather, drivers = await asyncio.gather(
            self.get_latest_positions(session_key),
            self.get_latest_intervals(session_key),
            self.get_stints(session_key),
            self.get_pit_stops(session_key),
            self.get_race_control(session_key),
            self.get_latest_weather(session_key),
            self.get_drivers(session_key),
        )

        # Index by driver number for easy lookup
        interval_map = {i["driver_number"]: i for i in intervals}
        pit_count = {}
        for p in pit_stops:
            d = p["driver_number"]
            pit_count[d] = pit_count.get(d, 0) + 1

        # Latest stint per driver
        latest_stint: dict[int, dict] = {}
        for s in stints:
            d = s["driver_number"]
            existing = latest_stint.get(d)
            if not existing or s.get("stint_number", 0) > existing.get("stint_number", 0):
                latest_stint[d] = s

        driver_map = {d["driver_number"]: d for d in drivers}

        # Determine SC / VSC
        flag_messages = [m for m in race_control if m.get("flag")]
        current_flag = None
        if flag_messages:
            current_flag = max(flag_messages, key=lambda m: m.get("date", "")).get("flag")

        sc_messages = [m for m in race_control if "SAFETY CAR" in (m.get("message") or "").upper()]
        vsc_messages = [m for m in race_control if "VIRTUAL" in (m.get("message") or "").upper()]
        safety_car_active = bool(sc_messages) and "ENDING" not in (sc_messages[-1].get("message") or "").upper()
        vsc_active = bool(vsc_messages) and "ENDING" not in (vsc_messages[-1].get("message") or "").upper()

        driver_states = []
        for pos in sorted(positions, key=lambda p: p.get("position", 99)):
            d_num = pos["driver_number"]
            d_info = driver_map.get(d_num, {})
            iv = interval_map.get(d_num, {})
            stint = latest_stint.get(d_num, {})

            driver_states.append({
                "driver_number": d_num,
                "name_acronym": d_info.get("name_acronym"),
                "full_name": d_info.get("full_name"),
                "team_name": d_info.get("team_name"),
                "team_colour": d_info.get("team_colour"),
                "position": pos.get("position"),
                "gap_to_leader": iv.get("gap_to_leader"),
                "interval": iv.get("interval"),
                "current_compound": stint.get("compound"),
                "tyre_age": stint.get("tyre_age_at_start"),
                "pit_stops": pit_count.get(d_num, 0),
                "last_updated": pos.get("date"),
            })

        return {
            "session_key": session_key,
            "latest_flag": current_flag,
            "safety_car_active": safety_car_active,
            "vsc_active": vsc_active,
            "drivers": driver_states,
            "weather": weather,
            "last_updated": datetime.utcnow().isoformat(),
        }

    async def close(self):
        await self._client.aclose()