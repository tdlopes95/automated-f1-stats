"""
F1 Backend - Race history ("on this day")
Past race winners come from app/data/race_winners.json (built by
scripts/build_race_winners.py); seasons newer than the file are merged in
from Jolpica at request time.
"""

import functools
import json
from datetime import date
from pathlib import Path

DATA_PATH = Path(__file__).parent / "data" / "race_winners.json"
MAX_WINDOW = 7

# Leap year, so 29 Feb has a slot of its own.
_REF_YEAR = 2000
_DAYS_IN_REF_YEAR = 366


def race_to_winners(race: dict) -> list[dict]:
    """A Jolpica race with P1 result row(s) -> one entry per winner (shared drives give two)."""
    circuit = race.get("Circuit", {})
    winners = []
    for result in race.get("Results", []):
        driver = result.get("Driver", {})
        constructor = result.get("Constructor", {})
        winners.append({
            "season": int(race.get("season", 0)),
            "round": int(race.get("round", 0)),
            "race_name": race.get("raceName"),
            "date": race.get("date"),
            "circuit_id": circuit.get("circuitId"),
            "circuit_name": circuit.get("circuitName"),
            "country": circuit.get("Location", {}).get("country"),
            "driver_id": driver.get("driverId"),
            "driver_name": f"{driver.get('givenName', '')} {driver.get('familyName', '')}".strip(),
            "constructor_id": constructor.get("constructorId"),
            "constructor_name": constructor.get("name"),
        })
    return winners


@functools.cache
def load_race_winners() -> list[dict]:
    """The bundled winners file, read once per process."""
    with DATA_PATH.open(encoding="utf-8") as f:
        return json.load(f)


def _day_of_year(d: date) -> int:
    return date(_REF_YEAR, d.month, d.day).timetuple().tm_yday


def month_day_distance(a: date, b: date) -> int:
    """Days between two month-days, ignoring the year and wrapping at New Year."""
    diff = abs(_day_of_year(a) - _day_of_year(b))
    return min(diff, _DAYS_IN_REF_YEAR - diff)


def on_this_day(winners: list[dict], target: date, window: int) -> list[dict]:
    """
    Races up to `target` whose month-day is within ±window days of it, closest
    month-day first, then most recent season first.
    """
    matches = []
    for winner in winners:
        try:
            race_day = date.fromisoformat(winner["date"])
        except (KeyError, TypeError, ValueError):
            continue
        if race_day > target:
            continue
        distance = month_day_distance(race_day, target)
        if distance <= window:
            matches.append((distance, {**winner, "years_ago": target.year - race_day.year}))

    matches.sort(key=lambda m: (m[0], -m[1]["season"], -m[1]["round"]))
    return [item for _, item in matches]
