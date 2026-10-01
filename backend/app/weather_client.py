"""
F1 Backend - Open-Meteo Weather Client
Race weekend forecasts at the circuit's coordinates.
https://open-meteo.com/en/docs
No API key needed. Free for non-commercial use, attribution required.
"""

import logging
from datetime import UTC, date, datetime, timedelta

import httpx

from .errors import UpstreamError

logger = logging.getLogger(__name__)
FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
ATTRIBUTION = "Weather data by Open-Meteo.com"

# Open-Meteo forecasts 16 days (today + 15); further out it rejects the request.
FORECAST_HORIZON_DAYS = 15

HOURLY_VARS = "temperature_2m,precipitation_probability,precipitation,wind_speed_10m,weather_code"
DAILY_VARS = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"


class OpenMeteoClient:
    def __init__(self):
        self._client = httpx.AsyncClient(timeout=10.0)

    async def get_forecast(self, lat: float, lng: float, start: date, end: date) -> dict:
        """
        Hourly and daily forecast for [start, end] in UTC.
        Network error, non-200 or invalid JSON -> UpstreamError("open-meteo").
        """
        params = {
            "latitude": lat,
            "longitude": lng,
            "hourly": HOURLY_VARS,
            "daily": DAILY_VARS,
            "timezone": "UTC",
            "start_date": start.isoformat(),
            "end_date": end.isoformat(),
        }
        try:
            response = await self._client.get(FORECAST_URL, params=params)
        except httpx.RequestError as e:
            logger.error(f"Open-Meteo request error: {e}")
            raise UpstreamError("open-meteo", "/v1/forecast") from e

        if response.status_code != 200:
            logger.error(f"Open-Meteo HTTP error {response.status_code}: {response.text[:200]}")
            raise UpstreamError("open-meteo", "/v1/forecast", response.status_code)
        try:
            return response.json()
        except ValueError as e:
            logger.error(f"Open-Meteo invalid JSON: {e}")
            raise UpstreamError("open-meteo", "/v1/forecast", response.status_code) from e

    async def close(self):
        await self._client.aclose()


def forecast_end_date(race_day: date, today: date) -> date:
    """The race day, clamped to the last day Open-Meteo will forecast."""
    return min(race_day, today + timedelta(days=FORECAST_HORIZON_DAYS))


def _at(values: list | None, index: int):
    if values is None or index >= len(values):
        return None
    return values[index]


def _as_int(value) -> int | None:
    return None if value is None else int(round(value))


def map_days(forecast: dict) -> list[dict]:
    daily = forecast.get("daily") or {}
    days = []
    for i, day in enumerate(daily.get("time") or []):
        days.append({
            "date": day,
            "weather_code": _as_int(_at(daily.get("weather_code"), i)),
            "temp_max": _at(daily.get("temperature_2m_max"), i),
            "temp_min": _at(daily.get("temperature_2m_min"), i),
            "rain_probability_max": _as_int(_at(daily.get("precipitation_probability_max"), i)),
        })
    return days


def map_sessions(forecast: dict, sessions: list[dict]) -> list[dict]:
    """
    One entry per schedule session, using the hourly slot at the session's start hour.
    A session outside the forecast range keeps its name and time with null values.
    """
    hourly = forecast.get("hourly") or {}
    slot_index = {slot: i for i, slot in enumerate(hourly.get("time") or [])}

    mapped = []
    for session in sessions:
        start = datetime.fromisoformat(session["datetime"])
        start = start.replace(tzinfo=UTC) if start.tzinfo is None else start.astimezone(UTC)
        i = slot_index.get(start.strftime("%Y-%m-%dT%H:00"))
        values = {}
        if i is not None:
            values = {
                "temperature": _at(hourly.get("temperature_2m"), i),
                "rain_probability": _as_int(_at(hourly.get("precipitation_probability"), i)),
                "precipitation": _at(hourly.get("precipitation"), i),
                "wind_speed": _at(hourly.get("wind_speed_10m"), i),
                "weather_code": _as_int(_at(hourly.get("weather_code"), i)),
            }
        mapped.append({"name": session["name"], "datetime_utc": start.isoformat(), **values})
    return mapped
