"""
Tests for /race-analysis/{year}/{round} and /circuit/{circuit_id}/pit-history.
All Jolpica traffic is mocked with respx.
"""

import asyncio
from datetime import UTC, datetime, timedelta

import httpx
import pytest
import respx

from app import main
from app.jolpica_client import JolpicaClient
from app.main import is_finisher, parse_duration_ms, summarize_pit_race
from tests.conftest import JOLPICA_BASE

CURRENT_YEAR = datetime.now(UTC).year


# ── Payload builders ─────────────────────────────────────────────────────────

def schedule(race_dt: datetime, round_number: int = 1, name: str = "Test Grand Prix") -> dict:
    return {"MRData": {"RaceTable": {"Races": [{
        "round": str(round_number),
        "raceName": name,
        "Circuit": {"circuitId": "monza", "circuitName": "Monza", "Location": {}},
        "date": race_dt.strftime("%Y-%m-%d"),
        "time": race_dt.strftime("%H:%M:%SZ"),
    }]}}}


def race_table(race: dict, total: int | None = None, offset: int = 0) -> dict:
    return {"MRData": {"total": str(total if total is not None else 1), "offset": str(offset),
                       "RaceTable": {"Races": [race]}}}


def driver(driver_id: str, code: str, given: str, family: str) -> dict:
    return {"driverId": driver_id, "code": code, "givenName": given, "familyName": family}


VER = driver("max_verstappen", "VER", "Max", "Verstappen")
LEC = driver("leclerc", "LEC", "Charles", "Leclerc")
SAI = driver("sainz", "SAI", "Carlos", "Sainz")
RBR = {"constructorId": "red_bull", "name": "Red Bull"}
FER = {"constructorId": "ferrari", "name": "Ferrari"}


def result(position, drv, team, grid, status, laps):
    return {"position": str(position), "grid": str(grid), "status": status, "laps": str(laps),
            "Driver": drv, "Constructor": team}


# Deliberately out of order: the endpoint sorts by classification.
RESULTS = [
    result(2, LEC, FER, 1, "Finished", 3),
    result(3, SAI, FER, 3, "Engine", 1),
    result(1, VER, RBR, 2, "Finished", 3),
]


def results_payload(results=RESULTS) -> dict:
    return race_table({"season": "2023", "round": "1", "Results": results})


def timing(driver_id, position, time):
    return {"driverId": driver_id, "position": str(position), "time": time}


# Two pages (total > PAGE_LIMIT): lap 2 straddles the page boundary.
LAPS_PAGES = {
    0: race_table({"season": "2023", "round": "1", "Laps": [
        {"number": "1", "Timings": [timing("leclerc", 1, "1:35.100"),
                                    timing("max_verstappen", 2, "1:35.400"),
                                    timing("sainz", 3, "1:36.000")]},
        {"number": "2", "Timings": [timing("leclerc", 1, "1:31.000")]},
    ]}, total=150),
    100: race_table({"season": "2023", "round": "1", "Laps": [
        {"number": "2", "Timings": [timing("max_verstappen", 2, "garbage")]},
        {"number": "3", "Timings": [timing("max_verstappen", 1, "1:30.500"),
                                    timing("leclerc", 2, "1:30.900")]},
    ]}, total=150, offset=100),
}

PIT_STOPS = race_table({"season": "2023", "round": "1", "PitStops": [
    {"driverId": "max_verstappen", "stop": "1", "lap": "2", "time": "15:10:00",
     "duration": "22.345"},
    {"driverId": "leclerc", "stop": "1", "lap": "2", "time": "15:10:05",
     "duration": "1:02.345"},
]})


def mock_laps(year: int, round_number: int = 1):
    return respx.get(f"{JOLPICA_BASE}/{year}/{round_number}/laps.json").mock(
        side_effect=lambda req: httpx.Response(200, json=LAPS_PAGES[int(req.url.params["offset"])])
    )


def mock_race(year: int, race_dt: datetime, results=RESULTS):
    respx.get(f"{JOLPICA_BASE}/{year}.json").mock(
        return_value=httpx.Response(200, json=schedule(race_dt)))
    return {
        "results": respx.get(f"{JOLPICA_BASE}/{year}/1/results.json").mock(
            return_value=httpx.Response(200, json=results_payload(results))),
        "laps": mock_laps(year),
        "pit_stops": respx.get(f"{JOLPICA_BASE}/{year}/1/pitstops.json").mock(
            return_value=httpx.Response(200, json=PIT_STOPS)),
    }


# ── Parsing ──────────────────────────────────────────────────────────────────

@pytest.mark.parametrize("value, expected", [
    ("1:23.456", 83456),
    ("23.456", 23456),
    ("1:02.345", 62345),
    ("1:02:03.456", 3723456),
    ("", None),
    ("   ", None),
    (None, None),
    ("abc", None),
    ("1:xx.1", None),
    ("-3.2", None),
    (12.3, None),
])
def test_parse_duration_ms(value, expected):
    assert parse_duration_ms(value) == expected


@pytest.mark.parametrize("status, expected", [
    ("Finished", True), ("+1 Lap", True), ("+3 Laps", True), ("Lapped", True),
    ("Engine", False), ("Accident", False), ("Disqualified", False), (None, False),
])
def test_is_finisher(status, expected):
    assert is_finisher(status) is expected


# ── Jolpica client ───────────────────────────────────────────────────────────

def test_jolpica_merges_lap_timings_across_pages():
    with respx.mock:
        mock_laps(2023)
        laps = asyncio.run(JolpicaClient().get_race_laps(2023, 1))

    assert [lap["number"] for lap in laps] == [1, 2, 3]
    assert [t["driverId"] for t in laps[1]["Timings"]] == ["leclerc", "max_verstappen"]


# ── /race-analysis ───────────────────────────────────────────────────────────

@respx.mock
def test_race_analysis_past_season(client):
    mock_race(2023, datetime(2023, 3, 5, 15, tzinfo=UTC))

    resp = client.get("/race-analysis/2023/1")
    assert resp.status_code == 200
    body = resp.json()

    assert body["race_name"] == "Test Grand Prix"
    assert body["total_laps"] == 3
    assert [d["driver_id"] for d in body["drivers"]] == ["max_verstappen", "leclerc", "sainz"]
    assert body["drivers"][0] == {
        "driver_id": "max_verstappen", "code": "VER", "name": "Max Verstappen",
        "constructor_id": "red_bull", "constructor_name": "Red Bull",
        "grid": 2, "final_position": 1, "status": "Finished",
    }

    assert body["laps_available"] is True
    assert body["positions"]["max_verstappen"] == [2, 2, 1]
    assert body["positions"]["sainz"] == [3, None, None]           # retired on lap 1
    assert body["lap_times_ms"]["max_verstappen"] == [95400, None, 90500]  # "garbage" -> null
    assert body["lap_times_ms"]["leclerc"] == [95100, 91000, 90900]

    assert body["pit_data_available"] is True
    assert body["pit_stops"] == [
        {"driver_id": "max_verstappen", "stop": 1, "lap": 2, "duration_ms": 22345},
        {"driver_id": "leclerc", "stop": 1, "lap": 2, "duration_ms": 62345},
    ]
    assert "pit-lane" in body["duration_note"]
    assert main._cache["race_analysis_2023_1"][2] == main.CACHE_TTL_FOREVER


@respx.mock
def test_race_analysis_pre_1996_has_no_laps(client):
    routes = mock_race(1990, datetime(1990, 3, 11, 15, tzinfo=UTC))

    resp = client.get("/race-analysis/1990/1")
    assert resp.status_code == 200
    body = resp.json()

    assert not routes["laps"].called
    assert not routes["pit_stops"].called
    assert body["laps_available"] is False
    assert body["positions"] == {}
    assert body["lap_times_ms"] == {}
    assert body["pit_data_available"] is False
    assert body["pit_stops"] == []
    assert body["total_laps"] == 3                                   # from the results' lap counts
    assert len(body["drivers"]) == 3


@respx.mock
def test_race_analysis_pre_2011_has_laps_but_no_pit_data(client):
    routes = mock_race(2005, datetime(2005, 3, 6, 15, tzinfo=UTC))

    body = client.get("/race-analysis/2005/1").json()

    assert routes["laps"].called
    assert not routes["pit_stops"].called
    assert body["laps_available"] is True
    assert body["pit_data_available"] is False
    assert body["pit_stops"] == []


@respx.mock
def test_race_analysis_future_race_returns_404(client):
    routes = mock_race(CURRENT_YEAR, datetime.now(UTC) + timedelta(days=5))

    resp = client.get(f"/race-analysis/{CURRENT_YEAR}/1")
    assert resp.status_code == 404
    assert not routes["results"].called


@respx.mock
def test_race_analysis_unknown_round_returns_404(client):
    mock_race(2023, datetime(2023, 3, 5, 15, tzinfo=UTC))
    assert client.get("/race-analysis/2023/9").status_code == 404


@respx.mock
def test_race_analysis_recent_race_cached_for_an_hour(client):
    mock_race(CURRENT_YEAR, datetime.now(UTC) - timedelta(hours=3))

    assert client.get(f"/race-analysis/{CURRENT_YEAR}/1").status_code == 200
    assert main._cache[f"race_analysis_{CURRENT_YEAR}_1"][2] == 3600


@respx.mock
def test_race_analysis_current_season_without_results_is_not_cached(client):
    mock_race(CURRENT_YEAR, datetime.now(UTC) - timedelta(hours=1), results=[])

    body = client.get(f"/race-analysis/{CURRENT_YEAR}/1").json()
    assert body["drivers"] == []
    assert f"race_analysis_{CURRENT_YEAR}_1" not in main._cache


@respx.mock
def test_race_analysis_upstream_error_returns_502(client):
    mock_race(2023, datetime(2023, 3, 5, 15, tzinfo=UTC))
    respx.get(f"{JOLPICA_BASE}/2023/1/pitstops.json").mock(
        return_value=httpx.Response(500, text="boom"))
    assert client.get("/race-analysis/2023/1").status_code == 502


# ── /circuit/{id}/pit-history ────────────────────────────────────────────────

def test_summarize_pit_race_averages_over_finishers():
    results = [
        result(1, VER, RBR, 1, "Finished", 53),
        result(2, LEC, FER, 2, "+1 Lap", 52),
        result(3, SAI, FER, 3, "Engine", 20),
    ]
    stops = [
        {"driverId": "max_verstappen", "stop": "1", "lap": "20", "duration": "23.100"},
        {"driverId": "leclerc", "stop": "1", "lap": "18", "duration": "22.900"},
        {"driverId": "leclerc", "stop": "2", "lap": "35", "duration": "bad"},
        {"driverId": "sainz", "stop": "1", "lap": "10", "duration": "22.500"},  # DNF
    ]
    summary = summarize_pit_race(2023, 14, "Italian Grand Prix", results, stops)

    assert summary["avg_stops_per_finisher"] == 1.5      # 3 finisher stops / 2 finishers
    assert summary["total_stops"] == 4
    assert summary["fastest_stop"] == {"driver_id": "sainz", "name": "Carlos Sainz",
                                       "duration_ms": 22500, "lap": 10}


def test_summarize_pit_race_without_pit_data():
    summary = summarize_pit_race(2023, 14, None, [result(1, VER, RBR, 1, "Finished", 53)], [])
    assert summary["avg_stops_per_finisher"] is None
    assert summary["total_stops"] == 0
    assert summary["fastest_stop"] is None


def winner_race(season: int, round_number: int) -> dict:
    return {"season": str(season), "round": str(round_number), "raceName": "Italian Grand Prix",
            "Circuit": {"circuitId": "monza"},
            "Results": [result(1, VER, RBR, 1, "Finished", 53)]}


@respx.mock
def test_circuit_pit_history(client):
    winners = {"MRData": {"total": "4", "RaceTable": {"Races": [
        winner_race(2010, 14), winner_race(2022, 16), winner_race(2023, 14), winner_race(2024, 16),
    ]}}}
    respx.get(f"{JOLPICA_BASE}/circuits/monza/results/1.json").mock(
        return_value=httpx.Response(200, json=winners))

    results = race_table({"Results": [result(1, VER, RBR, 1, "Finished", 53),
                                      result(2, LEC, FER, 2, "Finished", 53)]})
    stops = {
        2023: [{"driverId": "max_verstappen", "stop": "1", "lap": "20", "duration": "24.000"},
               {"driverId": "leclerc", "stop": "1", "lap": "19", "duration": "23.500"}],
        2024: [{"driverId": "max_verstappen", "stop": "1", "lap": "15", "duration": "22.000"},
               {"driverId": "max_verstappen", "stop": "2", "lap": "35", "duration": "22.800"},
               {"driverId": "leclerc", "stop": "1", "lap": "16", "duration": "23.000"}],
    }
    for season, round_number in ((2023, 14), (2024, 16)):
        respx.get(f"{JOLPICA_BASE}/{season}/{round_number}/results.json").mock(
            return_value=httpx.Response(200, json=results))
        respx.get(f"{JOLPICA_BASE}/{season}/{round_number}/pitstops.json").mock(
            return_value=httpx.Response(200, json=race_table({"PitStops": stops[season]})))

    resp = client.get("/circuit/monza/pit-history?seasons=2")
    assert resp.status_code == 200
    body = resp.json()

    assert [(r["season"], r["round"]) for r in body["races"]] == [(2023, 14), (2024, 16)]
    assert [r["avg_stops_per_finisher"] for r in body["races"]] == [1.0, 1.5]
    assert [r["total_stops"] for r in body["races"]] == [2, 3]
    assert body["races"][1]["fastest_stop"] == {"driver_id": "max_verstappen",
                                                "name": "Max Verstappen",
                                                "duration_ms": 22000, "lap": 15}
    assert body["races"][0]["race_name"] == "Italian Grand Prix"
    assert "pit-lane" in body["duration_note"]


def test_circuit_pit_history_seasons_capped(client):
    assert client.get("/circuit/monza/pit-history?seasons=16").status_code == 422


@respx.mock
def test_circuit_pit_history_unknown_circuit_returns_404(client):
    respx.get(f"{JOLPICA_BASE}/circuits/nowhere/results/1.json").mock(
        return_value=httpx.Response(200, json={"MRData": {"total": "0",
                                                          "RaceTable": {"Races": []}}}))
    assert client.get("/circuit/nowhere/pit-history").status_code == 404
