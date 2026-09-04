"""
End-to-end tests for the API endpoints.

Every outbound HTTP call to the Jolpica and OpenF1 APIs is mocked with respx,
so the suite never touches the network.
"""

import httpx
import pytest
import respx

from tests.conftest import JOLPICA_BASE, OPENF1_BASE

PAST_YEAR = 2023  # a completed season -> exercises the "historical" code paths


# ── Sample upstream payloads ─────────────────────────────────────────────────

SCHEDULE_PAYLOAD = {
    "MRData": {
        "RaceTable": {
            "Races": [
                {
                    "round": "1",
                    "raceName": "Bahrain Grand Prix",
                    "Circuit": {
                        "circuitId": "bahrain",
                        "circuitName": "Bahrain International Circuit",
                        "Location": {"country": "Bahrain", "locality": "Sakhir"},
                    },
                    "date": "2999-03-08",
                    "time": "15:00:00Z",
                }
            ]
        }
    }
}

RACE_RESULTS_PAYLOAD = {
    "MRData": {
        "RaceTable": {
            "Races": [
                {
                    "Results": [
                        {
                            "position": "1",
                            "grid": "1",
                            "Driver": {
                                "driverId": "max_verstappen",
                                "givenName": "Max",
                                "familyName": "Verstappen",
                            },
                            "Constructor": {
                                "constructorId": "red_bull",
                                "name": "Red Bull",
                            },
                        }
                    ]
                }
            ]
        }
    }
}

DRIVER_STANDINGS_PAYLOAD = {
    "MRData": {
        "StandingsTable": {
            "StandingsLists": [
                {
                    "DriverStandings": [
                        {"position": "1", "points": "575",
                         "Driver": {"driverId": "max_verstappen"}},
                        {"position": "2", "points": "285",
                         "Driver": {"driverId": "perez"}},
                    ]
                }
            ]
        }
    }
}

CONSTRUCTOR_STANDINGS_PAYLOAD = {
    "MRData": {
        "StandingsTable": {
            "StandingsLists": [
                {
                    "ConstructorStandings": [
                        {"position": "1", "points": "860",
                         "Constructor": {"constructorId": "red_bull"}},
                        {"position": "2", "points": "409",
                         "Constructor": {"constructorId": "mercedes"}},
                    ]
                }
            ]
        }
    }
}

MEETINGS_PAYLOAD = [
    {
        "meeting_key": 1217,
        "meeting_name": "Bahrain Grand Prix",
        "location": "Sakhir",
        "country_name": "Bahrain",
        "circuit_short_name": "Sakhir",
        "year": PAST_YEAR,
    }
]

SESSIONS_PAYLOAD = [
    {"session_key": 9000, "session_name": "Race", "session_type": "Race", "year": PAST_YEAR}
]


# ── Health check ─────────────────────────────────────────────────────────────

def test_root_health_check(client):
    resp = client.get("/")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok", "service": "F1 Backend API"}


# ── Schedule ─────────────────────────────────────────────────────────────────

@respx.mock
def test_get_schedule_success(client):
    route = respx.get(f"{JOLPICA_BASE}/current.json").mock(
        return_value=httpx.Response(200, json=SCHEDULE_PAYLOAD)
    )
    resp = client.get("/schedule")

    assert route.called
    assert resp.status_code == 200
    body = resp.json()
    assert len(body) == 1
    assert body[0]["round"] == 1
    assert body[0]["race_name"] == "Bahrain Grand Prix"
    assert {"name": "Race", "datetime": "2999-03-08T15:00:00+00:00"} in body[0]["sessions"]


@respx.mock
def test_get_next_race_not_found_when_no_races(client):
    # Upstream returns an empty calendar -> nothing upcoming -> 404.
    respx.get(f"{JOLPICA_BASE}/current.json").mock(
        return_value=httpx.Response(200, json={"MRData": {"RaceTable": {"Races": []}}})
    )
    resp = client.get("/schedule/next")
    assert resp.status_code == 404
    assert resp.json()["detail"] == "No upcoming race found"


# ── Results ──────────────────────────────────────────────────────────────────

@respx.mock
def test_get_results_success(client):
    route = respx.get(f"{JOLPICA_BASE}/{PAST_YEAR}/1/results.json").mock(
        return_value=httpx.Response(200, json=RACE_RESULTS_PAYLOAD)
    )
    resp = client.get(f"/results/{PAST_YEAR}/1")

    assert route.called
    assert resp.status_code == 200
    body = resp.json()
    assert body["source"] == "live"
    assert body["year"] == PAST_YEAR
    assert body["round"] == 1
    assert body["results"][0]["Driver"]["driverId"] == "max_verstappen"


@respx.mock
def test_get_results_upstream_error_returns_empty(client):
    # Jolpica 500s; the client swallows it and the endpoint degrades to no results.
    respx.get(f"{JOLPICA_BASE}/{PAST_YEAR}/2/results.json").mock(
        return_value=httpx.Response(500, text="upstream boom")
    )
    resp = client.get(f"/results/{PAST_YEAR}/2")
    assert resp.status_code == 200
    assert resp.json()["results"] == []


# ── Standings ────────────────────────────────────────────────────────────────

@respx.mock
def test_get_driver_standings_success(client):
    route = respx.get(f"{JOLPICA_BASE}/{PAST_YEAR}/driverStandings.json").mock(
        return_value=httpx.Response(200, json=DRIVER_STANDINGS_PAYLOAD)
    )
    resp = client.get(f"/standings/drivers?year={PAST_YEAR}")

    assert route.called
    assert resp.status_code == 200
    body = resp.json()
    assert body["source"] == "live"
    assert len(body["standings"]) == 2
    # leader gets an annotated gap to P2 (575 - 285)
    assert body["standings"][0]["gap_to_second"] == 290.0


@respx.mock
def test_get_constructor_standings_success(client):
    route = respx.get(f"{JOLPICA_BASE}/{PAST_YEAR}/constructorStandings.json").mock(
        return_value=httpx.Response(200, json=CONSTRUCTOR_STANDINGS_PAYLOAD)
    )
    resp = client.get(f"/standings/constructors?year={PAST_YEAR}")

    assert route.called
    assert resp.status_code == 200
    body = resp.json()
    assert body["source"] == "live"
    assert body["standings"][0]["Constructor"]["constructorId"] == "red_bull"


@respx.mock
def test_get_constructor_standings_upstream_error_returns_empty(client):
    respx.get(f"{JOLPICA_BASE}/{PAST_YEAR}/constructorStandings.json").mock(
        return_value=httpx.Response(503, text="unavailable")
    )
    resp = client.get(f"/standings/constructors?year={PAST_YEAR}")
    assert resp.status_code == 200
    assert resp.json()["standings"] == []


# ── OpenF1-backed endpoints ──────────────────────────────────────────────────

@respx.mock
def test_get_meetings_success(client):
    route = respx.get(f"{OPENF1_BASE}/meetings").mock(
        return_value=httpx.Response(200, json=MEETINGS_PAYLOAD)
    )
    resp = client.get(f"/meetings?year={PAST_YEAR}")

    assert route.called
    assert resp.status_code == 200
    body = resp.json()
    assert body[0]["meeting_name"] == "Bahrain Grand Prix"
    assert body[0]["country_name"] == "Bahrain"


@respx.mock
def test_get_sessions_success(client):
    route = respx.get(f"{OPENF1_BASE}/sessions").mock(
        return_value=httpx.Response(200, json=SESSIONS_PAYLOAD)
    )
    resp = client.get(f"/sessions?year={PAST_YEAR}&session_type=Race")

    assert route.called
    assert resp.status_code == 200
    assert resp.json()[0]["session_key"] == 9000


# ── Circuit stats ────────────────────────────────────────────────────────────

@respx.mock
def test_get_circuit_stats_success(client):
    circuit_races = {
        "MRData": {
            "total": "1",
            "RaceTable": {
                "Races": [
                    {
                        "season": "2023",
                        "Circuit": {
                            "circuitName": "Autodromo Nazionale di Monza",
                            "Location": {"locality": "Monza", "country": "Italy"},
                        },
                        "Results": [
                            {
                                "position": "1",
                                "grid": "1",
                                "Driver": {"driverId": "max_verstappen",
                                           "givenName": "Max", "familyName": "Verstappen"},
                                "Constructor": {"constructorId": "red_bull", "name": "Red Bull"},
                            }
                        ],
                    }
                ]
            },
        }
    }
    respx.get(f"{JOLPICA_BASE}/circuits/monza/results.json").mock(
        return_value=httpx.Response(200, json=circuit_races)
    )
    resp = client.get("/circuit/monza/stats")

    assert resp.status_code == 200
    body = resp.json()
    assert body["circuitId"] == "monza"
    assert body["totalRaces"] == 1
    assert body["mostWins"]["name"] == "Max Verstappen"


@respx.mock
def test_get_circuit_stats_upstream_error(client):
    respx.get(f"{JOLPICA_BASE}/circuits/monza/results.json").mock(
        return_value=httpx.Response(500, text="upstream boom")
    )
    resp = client.get("/circuit/monza/stats")
    # No data could be fetched -> 404 rather than a 500 leaking out.
    assert resp.status_code == 404
