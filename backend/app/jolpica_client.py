"""
F1 Backend - Jolpica API Client
Race calendar, historical results, standings
https://api.jolpi.ca/ergast/f1/
No API key needed. Free for non-commercial use.
"""

import logging
from datetime import datetime, date
from typing import Optional

import httpx

logger = logging.getLogger(__name__)
BASE_URL = "https://api.jolpi.ca/ergast/f1"


class JolpicaClient:
    def __init__(self):
        self._client = httpx.AsyncClient(
            base_url=BASE_URL,
            timeout=15.0
        )

    async def _get(self, path: str, params: dict = None) -> dict:
        try:
            response = await self._client.get(path, params=params)
            response.raise_for_status()
            return response.json()
        except httpx.HTTPStatusError as e:
            logger.error(f"Jolpica HTTP error {e.response.status_code} on {path}: {e}")
            return {}
        except httpx.RequestError as e:
            logger.error(f"Jolpica request error on {path}: {e}")
            return {}

    # ── Race Schedule / Calendar ─────────────────────────────────────────────

    async def get_schedule(self, year: int = None) -> list[dict]:
        """
        Returns the full race calendar for a season.
        Each item includes: raceName, Circuit, date, time, plus
        optional FirstPractice, SecondPractice, ThirdPractice,
        Qualifying, Sprint, SprintQualifying session datetimes.
        """
        season = str(year) if year else "current"
        data = await self._get(f"/{season}.json", {"limit": 30})
        races = data.get("MRData", {}).get("RaceTable", {}).get("Races", [])

        schedule = []
        for race in races:
            entry = {
                "round": int(race.get("round", 0)),
                "race_name": race.get("raceName"),
                "circuit": race.get("Circuit", {}).get("circuitName"),
                "country": race.get("Circuit", {}).get("Location", {}).get("country"),
                "locality": race.get("Circuit", {}).get("Location", {}).get("locality"),
                "sessions": self._parse_sessions(race),
            }
            schedule.append(entry)
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

    def _to_datetime(self, date_str: Optional[str], time_str: Optional[str]) -> Optional[datetime]:
        if not date_str:
            return None
        try:
            if time_str:
                time_str = time_str.rstrip("Z")
                return datetime.fromisoformat(f"{date_str}T{time_str}")
            return datetime.fromisoformat(date_str)
        except ValueError:
            return None

    async def get_next_race(self) -> Optional[dict]:
        """Returns the next upcoming race from the current season."""
        schedule = await self.get_schedule()
        today = date.today()
        for race in schedule:
            for session in race.get("sessions", []):
                if session["name"] == "Race":
                    race_date = datetime.fromisoformat(session["datetime"]).date()
                    if race_date >= today:
                        return race
        return None

    async def get_upcoming_sessions(self, days_ahead: int = 14) -> list[dict]:
        """
        Returns all sessions happening within the next N days.
        Used by the scheduler to arm jobs.
        """
        from datetime import timedelta
        schedule = await self.get_schedule()
        now = datetime.utcnow()
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

    async def get_constructor_standings(self, year: int = None, round_number: int = None) -> list[dict]:
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

    async def close(self):
        await self._client.aclose()