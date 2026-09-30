"""
End-to-end tests for the API endpoints.

Every outbound HTTP call to the Jolpica and OpenF1 APIs is mocked with respx,
so the suite never touches the network.
"""

from datetime import datetime, timedelta, timezone

import httpx
import respx

from app.main import compute_circuit_stats, last_completed_round
from app.scheduler import F1Scheduler
from tests.conftest import JOLPICA_BASE, OPENF1_BASE

PAST_YEAR = 2023  # a completed season -> exercises the "historical" code paths
CURRENT_YEAR = datetime.now(timezone.utc).year


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
    route = respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}.json").mock(
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
    respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}.json").mock(
        return_value=httpx.Response(200, json={"MRData": {"RaceTable": {"Races": []}}})
    )
    resp = client.get("/schedule/next")
    assert resp.status_code == 404
    assert resp.json()["detail"] == "No upcoming race found"


@respx.mock
def test_schedule_next_uses_cached_schedule(client):
    route = respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}.json").mock(
        return_value=httpx.Response(200, json=SCHEDULE_PAYLOAD)
    )
    first = client.get("/schedule/next")
    second = client.get("/schedule/next")

    assert first.status_code == second.status_code == 200
    assert second.json()["race_name"] == "Bahrain Grand Prix"
    assert route.call_count == 1


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
def test_get_results_upstream_error_returns_502(client):
    # Jolpica 500s -> surfaced as 502, never as a 200 with empty data.
    respx.get(f"{JOLPICA_BASE}/{PAST_YEAR}/2/results.json").mock(
        return_value=httpx.Response(500, text="upstream boom")
    )
    resp = client.get(f"/results/{PAST_YEAR}/2")
    assert resp.status_code == 502
    assert resp.json() == {"detail": "Upstream data source unavailable", "source": "jolpica"}


@respx.mock
def test_get_results_not_found_upstream_returns_empty(client):
    # A Jolpica 404 means "no data", not a failure.
    respx.get(f"{JOLPICA_BASE}/{PAST_YEAR}/3/results.json").mock(
        return_value=httpx.Response(404, text="not found")
    )
    resp = client.get(f"/results/{PAST_YEAR}/3")
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
def test_get_constructor_standings_upstream_error_returns_502(client):
    respx.get(f"{JOLPICA_BASE}/{PAST_YEAR}/constructorStandings.json").mock(
        return_value=httpx.Response(503, text="unavailable")
    )
    resp = client.get(f"/standings/constructors?year={PAST_YEAR}")
    assert resp.status_code == 502
    assert resp.json()["source"] == "jolpica"


@respx.mock
def test_current_driver_standings_upstream_error_serves_stale_row(client, stub_db):
    stored = [{"position": "1", "points": "300", "gap_to_second": 12.0,
               "Driver": {"driverId": "norris"}}]
    stub_db.driver_standings[CURRENT_YEAR] = stored
    respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}.json").mock(
        return_value=httpx.Response(200, json=SCHEDULE_PAYLOAD)
    )
    respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}/driverStandings.json").mock(
        return_value=httpx.Response(500, text="upstream boom")
    )
    resp = client.get("/standings/drivers")

    assert resp.status_code == 200
    body = resp.json()
    assert body["source"] == "stale"
    assert body["year"] == CURRENT_YEAR
    assert body["standings"] == stored


@respx.mock
def test_current_driver_standings_upstream_error_without_row_returns_502(client):
    respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}.json").mock(
        return_value=httpx.Response(200, json=SCHEDULE_PAYLOAD)
    )
    respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}/driverStandings.json").mock(
        return_value=httpx.Response(500, text="upstream boom")
    )
    resp = client.get("/standings/drivers")
    assert resp.status_code == 502


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


# ── Live ─────────────────────────────────────────────────────────────────────

LIVE_SESSION = {"session_key": 9000, "session_name": "Race", "session_type": "Race",
                "date_end": "2025-12-07T15:00:00+00:00", "year": 2025}


@respx.mock
def test_live_on_demand_snapshot_is_not_live_and_keeps_data_timestamp(client, stub_db):
    respx.get(f"{OPENF1_BASE}/sessions").mock(return_value=httpx.Response(200, json=[LIVE_SESSION]))
    respx.get(f"{OPENF1_BASE}/position").mock(return_value=httpx.Response(200, json=[
        {"driver_number": 1, "position": 1, "date": "2025-12-07T14:58:00+00:00"},
        {"driver_number": 4, "position": 2, "date": "2025-12-07T14:59:30+00:00"},
    ]))
    respx.get(f"{OPENF1_BASE}/drivers").mock(return_value=httpx.Response(200, json=[
        {"driver_number": 1, "name_acronym": "VER"}, {"driver_number": 4, "name_acronym": "NOR"},
    ]))
    for endpoint in ("intervals", "stints", "pit", "race_control", "weather"):
        respx.get(f"{OPENF1_BASE}/{endpoint}").mock(return_value=httpx.Response(404))

    resp = client.get("/live")

    assert resp.status_code == 200
    body = resp.json()
    assert body["is_live"] is False
    assert body["session_key"] == 9000
    assert body["session_type"] == "Race"
    assert body["last_updated"] == "2025-12-07T14:59:30+00:00"   # newest position, not now()
    assert body["captured_at"][:4] == str(CURRENT_YEAR)
    assert [d["name_acronym"] for d in body["drivers"]] == ["VER", "NOR"]
    assert stub_db.saved_snapshots == 0                              # on-demand -> not stored


# ── Scheduler ────────────────────────────────────────────────────────────────

class FakeJolpica:
    async def get_upcoming_sessions(self, days_ahead=14):
        return []


def _upcoming(hours_from_now, name="Race", round_number=5):
    return {"race_name": "Test GP", "country": "Nowhere", "round": round_number,
            "session_name": name,
            "session_datetime": datetime.now(timezone.utc) + timedelta(hours=hours_from_now)}


async def _noop(*a, **kw):
    return None


def _arm(session, live_polling_enabled):
    import asyncio

    async def go():
        sched = F1Scheduler(FakeJolpica(), _noop, _noop, live_polling_enabled=live_polling_enabled)
        sched.scheduler.start(paused=True)
        try:
            await sched._arm_session(session)
            return sched, {job.id for job in sched.scheduler.get_jobs()}
        finally:
            sched.scheduler.shutdown(wait=False)
    return asyncio.run(go())


def test_scheduler_without_token_arms_results_but_not_live_poll():
    session = _upcoming(1, name="Sprint Qualifying")
    sched, job_ids = _arm(session, live_polling_enabled=False)
    year = session["session_datetime"].year
    assert job_ids == {f"results_{year}_5_Sprint_Qualifying"}
    assert list(sched._armed_sessions) == [f"{year}_5_Sprint_Qualifying"]


def test_scheduler_with_token_arms_both_jobs_with_year_in_ids():
    session = _upcoming(1)
    _, job_ids = _arm(session, live_polling_enabled=True)
    year = session["session_datetime"].year
    assert job_ids == {f"live_poll_{year}_5_Race", f"results_{year}_5_Race"}


def test_refresh_schedule_forgets_sessions_whose_results_time_passed():
    import asyncio
    sched = F1Scheduler(FakeJolpica(), _noop, _noop, live_polling_enabled=False)
    now = datetime.now(timezone.utc)
    sched._armed_sessions = {"old": now - timedelta(minutes=1), "new": now + timedelta(hours=1)}
    asyncio.run(sched.refresh_schedule())
    assert list(sched._armed_sessions) == ["new"]


# ── Circuit stats ────────────────────────────────────────────────────────────

MONZA = {
    "circuitName": "Autodromo Nazionale di Monza",
    "Location": {"locality": "Monza", "country": "Italy"},
}


def _driver(driver_id, given, family):
    return {"driverId": driver_id, "givenName": given, "familyName": family}


def _circuit_race(season, round_number, results):
    return {"season": str(season), "round": str(round_number), "Circuit": MONZA, "Results": results}


def _race_table(races, total=None, offset=0):
    return {"MRData": {"total": str(len(races) if total is None else total),
                       "offset": str(offset), "RaceTable": {"Races": races}}}


VER = _driver("max_verstappen", "Max", "Verstappen")
LEC = _driver("leclerc", "Charles", "Leclerc")
RBR = {"constructorId": "red_bull", "name": "Red Bull"}
FER = {"constructorId": "ferrari", "name": "Ferrari"}


def _mock_circuit_routes(winners, poles, fastest):
    base = f"{JOLPICA_BASE}/circuits/monza"
    return (
        respx.get(f"{base}/results/1.json").mock(return_value=httpx.Response(200, json=winners)),
        respx.get(f"{base}/grid/1/results.json").mock(return_value=httpx.Response(200, json=poles)),
        respx.get(f"{base}/fastest/1/results.json").mock(return_value=httpx.Response(200, json=fastest)),
    )


@respx.mock
def test_get_circuit_stats_success(client):
    winners = _race_table([
        _circuit_race(2022, 16, [{"position": "1", "Driver": VER, "Constructor": RBR}]),
        _circuit_race(2023, 14, [{"position": "1", "Driver": VER, "Constructor": RBR}]),
        _circuit_race(2024, 16, [{"position": "1", "Driver": LEC, "Constructor": FER}]),
    ])
    poles = _race_table([
        _circuit_race(2022, 16, [{"grid": "1", "Driver": LEC, "Constructor": FER}]),
        _circuit_race(2023, 14, [{"grid": "1", "Driver": VER, "Constructor": RBR}]),
        _circuit_race(2024, 16, [{"grid": "1", "Driver": LEC, "Constructor": FER}]),
    ])
    fastest = _race_table([
        _circuit_race(1959, 8, [{"Driver": LEC, "FastestLap": {"rank": "1"}}]),  # untimed
        _circuit_race(2023, 14, [{"Driver": VER, "FastestLap": {"rank": "1", "Time": {"time": "1:25.072"}}}]),
        _circuit_race(2024, 16, [{"Driver": LEC, "FastestLap": {"rank": "1", "Time": {"time": "1:21.432"}}}]),
    ])
    routes = _mock_circuit_routes(winners, poles, fastest)
    resp = client.get("/circuit/monza/stats")

    assert all(r.called for r in routes)
    assert resp.status_code == 200
    body = resp.json()
    assert body["circuitId"] == "monza"
    assert body["circuitName"] == "Autodromo Nazionale di Monza"
    assert body["country"] == "Italy"
    assert body["totalRaces"] == 3
    assert (body["firstGPYear"], body["lastGPYear"]) == (2022, 2024)
    assert body["mostWins"] == {"driverId": "max_verstappen", "name": "Max Verstappen",
                                "count": 2, "years": [2022, 2023]}
    assert body["mostConstructorWins"]["constructorId"] == "red_bull"
    assert body["mostPoles"]["driverId"] == "leclerc"
    assert body["mostPoles"]["count"] == 2
    assert body["lapRecord"] == {"driverId": "leclerc", "name": "Charles Leclerc",
                                 "time": "1:21.432", "year": 2024}
    assert body["lapRecordSinceYear"] == 2023
    assert body["dataNote"].startswith("Poles counted as starts from grid 1.")


@respx.mock
def test_get_circuit_stats_upstream_error(client):
    empty = _race_table([])
    base = f"{JOLPICA_BASE}/circuits/monza"
    respx.get(f"{base}/results/1.json").mock(return_value=httpx.Response(200, json=empty))
    respx.get(f"{base}/grid/1/results.json").mock(return_value=httpx.Response(500, text="boom"))
    respx.get(f"{base}/fastest/1/results.json").mock(return_value=httpx.Response(200, json=empty))
    resp = client.get("/circuit/monza/stats")
    assert resp.status_code == 502
    assert resp.json()["source"] == "jolpica"


@respx.mock
def test_get_circuit_stats_paginates_and_merges_straddling_race(client):
    # 150 P1 rows over two pages; the 2020 race (a shared win) straddles the boundary.
    page1 = [_circuit_race(1900 + i, 1, [{"position": "1", "Driver": VER, "Constructor": RBR}])
             for i in range(99)]
    page1.append(_circuit_race(2020, 1, [{"position": "1", "Driver": VER, "Constructor": RBR}]))
    page2 = [_circuit_race(2020, 1, [{"position": "1", "Driver": LEC, "Constructor": FER}])]
    page2 += [_circuit_race(2021 + i, 1, [{"position": "1", "Driver": LEC, "Constructor": FER}])
              for i in range(49)]

    def winners_page(request):
        offset = int(request.url.params["offset"])
        assert request.url.params["limit"] == "100"
        races = page1 if offset == 0 else page2
        return httpx.Response(200, json=_race_table(races, total=150, offset=offset))

    empty = _race_table([])
    base = f"{JOLPICA_BASE}/circuits/monza"
    winners_route = respx.get(f"{base}/results/1.json").mock(side_effect=winners_page)
    respx.get(f"{base}/grid/1/results.json").mock(return_value=httpx.Response(200, json=empty))
    respx.get(f"{base}/fastest/1/results.json").mock(return_value=httpx.Response(200, json=empty))

    resp = client.get("/circuit/monza/stats")

    assert winners_route.call_count == 2
    assert resp.status_code == 200
    body = resp.json()
    assert body["totalRaces"] == 149                     # 150 rows, one race split across pages
    assert body["mostWins"]["count"] == 100              # 99 + the shared 2020 win
    assert body["lastGPYear"] == 2069


def test_jolpica_merges_results_across_pages():
    import asyncio
    from app.jolpica_client import JolpicaClient

    pages = {
        0: _race_table([_circuit_race(2020, 1, [{"position": "1"}])], total=150),
        100: _race_table([_circuit_race(2020, 1, [{"position": "2"}]),
                          _circuit_race(2021, 1, [{"position": "1"}])], total=150, offset=100),
    }
    with respx.mock:
        respx.get(f"{JOLPICA_BASE}/circuits/monza/results.json").mock(
            side_effect=lambda req: httpx.Response(200, json=pages[int(req.url.params["offset"])])
        )
        races = asyncio.run(JolpicaClient()._get_all_races("/circuits/monza/results.json"))

    assert [(r["season"], r["round"]) for r in races] == [("2020", "1"), ("2021", "1")]
    assert [x["position"] for x in races[0]["Results"]] == ["1", "2"]


def test_compute_circuit_stats_most_wins_tie_prefers_most_recent():
    winners = [
        _circuit_race(2010, 1, [{"Driver": LEC, "Constructor": FER}]),
        _circuit_race(2011, 1, [{"Driver": VER, "Constructor": RBR}]),
        _circuit_race(2012, 1, [{"Driver": LEC, "Constructor": FER}]),
        _circuit_race(2013, 1, [{"Driver": VER, "Constructor": RBR}]),
    ]
    stats = compute_circuit_stats("monza", winners, [], [])
    # both on 2 wins; Verstappen's latest win (2013) is more recent than Leclerc's (2012)
    assert stats["mostWins"]["driverId"] == "max_verstappen"
    assert stats["mostConstructorWins"]["constructorId"] == "red_bull"
    assert stats["lapRecord"] is None and stats["lapRecordSinceYear"] is None


# ── Helpers ──────────────────────────────────────────────────────────────────

def _race(round_number, race_dt):
    return {"round": round_number,
            "sessions": [{"name": "Qualifying", "datetime": (race_dt - timedelta(days=1)).isoformat()},
                         {"name": "Race", "datetime": race_dt.isoformat()}]}


def test_last_completed_round():
    now = datetime(2026, 6, 1, 14, 0, tzinfo=timezone.utc)
    schedule = [
        _race(1, now - timedelta(days=14)),
        _race(2, now - timedelta(hours=1)),      # started an hour ago -> counts
        _race(3, now + timedelta(hours=1)),      # same day, later -> does not count
        _race(4, now + timedelta(days=14)),
    ]
    assert last_completed_round(schedule, now=now) == 2
    assert last_completed_round(schedule[2:], now=now) == 0
    assert last_completed_round([], now=now) == 0
