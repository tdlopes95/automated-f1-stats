"""
Tests for the weather forecast, news headlines and on-this-day history endpoints.

All outbound HTTP (Jolpica, Open-Meteo, RSS feeds) is mocked with respx.
"""

from datetime import UTC, date, datetime, timedelta

import httpx
import respx

from app import history, news_client
from app.history import month_day_distance
from tests.conftest import JOLPICA_BASE

CURRENT_YEAR = datetime.now(UTC).year
OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast"


# ── Weather ──────────────────────────────────────────────────────────────────

def _jolpica_date_time(dt: datetime) -> dict:
    return {"date": dt.strftime("%Y-%m-%d"), "time": dt.strftime("%H:%M:%SZ")}


def weekend_schedule(fp1: datetime, quali: datetime, race: datetime) -> dict:
    return {"MRData": {"RaceTable": {"Races": [{
        "round": "5",
        "raceName": "Test Grand Prix",
        "Circuit": {
            "circuitId": "test_ring",
            "circuitName": "Test Ring",
            "Location": {"lat": "26.0325", "long": "50.5106",
                         "locality": "Sakhir", "country": "Bahrain"},
        },
        **_jolpica_date_time(race),
        "FirstPractice": _jolpica_date_time(fp1),
        "Qualifying": _jolpica_date_time(quali),
    }]}}}


def _hour(dt: datetime) -> datetime:
    return dt.replace(minute=0, second=0, microsecond=0)


@respx.mock
def test_schedule_includes_circuit_coordinates(client):
    now = datetime.now(UTC)
    respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}.json").mock(return_value=httpx.Response(
        200, json=weekend_schedule(now, now, now)
    ))
    race = client.get("/schedule").json()[0]

    assert race["lat"] == 26.0325
    assert race["lng"] == 50.5106


@respx.mock
def test_weather_too_far_ahead_is_unavailable_without_calling_open_meteo(client):
    fp1 = _hour(datetime.now(UTC) + timedelta(days=40)).replace(hour=11, minute=30)
    respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}.json").mock(return_value=httpx.Response(
        200, json=weekend_schedule(fp1, fp1 + timedelta(days=1), fp1 + timedelta(days=2))
    ))
    open_meteo = respx.get(OPEN_METEO_URL).mock(return_value=httpx.Response(500))

    resp = client.get(f"/weather/{CURRENT_YEAR}/5")

    assert resp.status_code == 200
    body = resp.json()
    assert body["available"] is False
    assert body["available_from"] == (fp1.date() - timedelta(days=15)).isoformat()
    assert body["days"] == [] and body["sessions"] == []
    assert body["race_name"] == "Test Grand Prix"
    assert body["attribution"] == "Weather data by Open-Meteo.com"
    assert not open_meteo.called


@respx.mock
def test_weather_maps_forecast_to_sessions(client):
    fp1 = _hour(datetime.now(UTC) + timedelta(days=2)).replace(hour=11, minute=30)
    quali = fp1.replace(hour=15, minute=0) + timedelta(days=1)
    race = fp1.replace(hour=13, minute=0) + timedelta(days=2)
    respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}.json").mock(return_value=httpx.Response(
        200, json=weekend_schedule(fp1, quali, race)
    ))

    days = [(fp1.date() + timedelta(days=i)).isoformat() for i in range(3)]
    slots = [f"{d}T{h:02d}:00" for d in days for h in range(24)]
    temps = [20.0 + i * 0.1 for i in range(len(slots))]

    def slot(dt):
        return slots.index(dt.strftime("%Y-%m-%dT%H:00"))

    rain = [0] * len(slots)
    rain[slot(race)] = 70
    forecast = {
        "hourly": {
            "time": slots,
            "temperature_2m": temps,
            "precipitation_probability": rain,
            "precipitation": [0.0] * len(slots),
            "wind_speed_10m": [12.5] * len(slots),
            "weather_code": [61 if r else 1 for r in rain],
        },
        "daily": {
            "time": days,
            "weather_code": [1, 2, 61],
            "temperature_2m_max": [30.1, 29.4, 27.0],
            "temperature_2m_min": [21.0, 20.5, 19.8],
            "precipitation_probability_max": [5, 10, 70],
        },
    }
    route = respx.get(OPEN_METEO_URL).mock(return_value=httpx.Response(200, json=forecast))

    resp = client.get(f"/weather/{CURRENT_YEAR}/5")

    assert resp.status_code == 200
    params = route.calls.last.request.url.params
    assert params["start_date"] == days[0]
    assert params["end_date"] == days[2]
    assert params["timezone"] == "UTC"
    assert params["latitude"] == "26.0325"

    body = resp.json()
    assert body["available"] is True
    assert body["available_from"] is None
    assert body["days"][2] == {"date": days[2], "weather_code": 61, "temp_max": 27.0,
                               "temp_min": 19.8, "rain_probability_max": 70}

    by_name = {s["name"]: s for s in body["sessions"]}
    assert list(by_name) == ["Practice 1", "Qualifying", "Race"]
    # FP1 starts at 11:30 -> the 11:00 slot
    assert by_name["Practice 1"]["temperature"] == temps[slot(fp1)]
    assert by_name["Practice 1"]["datetime_utc"] == fp1.isoformat()
    assert by_name["Race"]["rain_probability"] == 70
    assert by_name["Race"]["weather_code"] == 61
    assert by_name["Race"]["wind_speed"] == 12.5


@respx.mock
def test_weather_open_meteo_failure_returns_502(client):
    fp1 = datetime.now(UTC) + timedelta(days=1)
    respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}.json").mock(return_value=httpx.Response(
        200, json=weekend_schedule(fp1, fp1 + timedelta(days=1), fp1 + timedelta(days=2))
    ))
    respx.get(OPEN_METEO_URL).mock(return_value=httpx.Response(500, text="boom"))

    resp = client.get(f"/weather/{CURRENT_YEAR}/5")

    assert resp.status_code == 502
    assert resp.json()["source"] == "open-meteo"


@respx.mock
def test_weather_past_race_returns_404(client):
    race = datetime.now(UTC) - timedelta(days=10)
    respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}.json").mock(return_value=httpx.Response(
        200, json=weekend_schedule(race - timedelta(days=2), race - timedelta(days=1), race)
    ))
    open_meteo = respx.get(OPEN_METEO_URL).mock(return_value=httpx.Response(500))

    resp = client.get(f"/weather/{CURRENT_YEAR}/5")

    assert resp.status_code == 404
    assert not open_meteo.called


# ── News ─────────────────────────────────────────────────────────────────────

FEED_A = "https://feeds.example.com/a.xml"
FEED_B = "https://feeds.example.com/b.xml"
FEED_C = "https://feeds.example.com/broken.xml"


def rss(items: list[str]) -> str:
    return ('<?xml version="1.0" encoding="UTF-8"?><rss version="2.0"><channel>'
            "<title>Test</title>" + "".join(items) + "</channel></rss>")


def item(title: str, link: str, pub: str | None) -> str:
    pub_xml = f"<pubDate>{pub}</pubDate>" if pub else ""
    return f"<item><title>{title}</title><link>{link}</link>{pub_xml}</item>"


@respx.mock
def test_news_merges_dedupes_sorts_and_reports_failed_feed(client, monkeypatch):
    monkeypatch.setattr(news_client, "FEEDS", [
        {"source": "Feed A", "url": FEED_A},
        {"source": "Feed B", "url": FEED_B},
        {"source": "Broken", "url": FEED_C},
    ])
    respx.get(FEED_A).mock(return_value=httpx.Response(200, text=rss([
        item("Verstappen wins in Monza", "https://a.example.com/monza",
             "Sun, 07 Sep 2025 16:00:00 +0000"),
        item("Norris on &lt;b&gt;pole&lt;/b&gt;", "https://a.example.com/pole",
             "Sat, 06 Sep 2025 15:30:00 +0000"),
        item("No link here", "", "Sat, 06 Sep 2025 10:00:00 +0000"),
        item("", "https://a.example.com/untitled", "Sat, 06 Sep 2025 10:00:00 +0000"),
    ])))
    respx.get(FEED_B).mock(return_value=httpx.Response(200, text=rss([
        # Same story as Feed A's, different link and punctuation/case
        item("Verstappen WINS in Monza!", "https://b.example.com/vers-monza",
             "Sun, 07 Sep 2025 16:05:00 +0000"),
        item("Team news", "https://b.example.com/team", "Sun, 07 Sep 2025 18:00:00 +0000"),
        item("Undated story", "https://b.example.com/undated", None),
    ])))
    respx.get(FEED_C).mock(return_value=httpx.Response(503))

    resp = client.get("/news")

    assert resp.status_code == 200
    body = resp.json()
    assert body["failed_sources"] == ["Broken"]
    titles = [i["title"] for i in body["items"]]
    # newest first, the duplicate kept once (the newer copy), undated last, HTML stripped
    assert titles == ["Team news", "Verstappen WINS in Monza!", "Norris on pole",
                      "Undated story"]
    assert body["items"][0] == {"title": "Team news", "link": "https://b.example.com/team",
                                "source": "Feed B",
                                "published_utc": "2025-09-07T18:00:00+00:00"}
    assert body["items"][-1]["published_utc"] is None

    assert [i["title"] for i in client.get("/news?limit=2").json()["items"]] == titles[:2]
    assert client.get("/news?limit=51").status_code == 422


@respx.mock
def test_news_all_feeds_failing_returns_502(client, monkeypatch):
    monkeypatch.setattr(news_client, "FEEDS", [
        {"source": "Feed A", "url": FEED_A},
        {"source": "Feed B", "url": FEED_B},
    ])
    respx.get(FEED_A).mock(side_effect=httpx.ConnectError("down"))
    respx.get(FEED_B).mock(return_value=httpx.Response(200, text="<html>not a feed</html>"))

    resp = client.get("/news")

    assert resp.status_code == 502
    assert resp.json()["source"] == "news"


# ── History ──────────────────────────────────────────────────────────────────

def winner(season: int, day: str, driver_id: str, round_number: int = 1) -> dict:
    return {
        "season": season, "round": round_number, "race_name": f"{driver_id} GP",
        "date": f"{season}-{day}", "circuit_id": "c", "circuit_name": "Circuit",
        "country": "Somewhere", "driver_id": driver_id, "driver_name": driver_id.title(),
        "constructor_id": "team", "constructor_name": "Team",
    }


def season_winners_payload(races: list[dict]) -> dict:
    return {"MRData": {"total": str(len(races)), "RaceTable": {"Races": races}}}


def use_bundled(monkeypatch, winners: list[dict]):
    monkeypatch.setattr(history, "load_race_winners", lambda: winners)


def mock_current_season(races: list[dict]):
    return respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}/results/1.json").mock(
        return_value=httpx.Response(200, json=season_winners_payload(races))
    )


def test_month_day_distance_wraps_at_new_year():
    assert month_day_distance(date(2025, 12, 30), date(1965, 1, 1)) == 2
    assert month_day_distance(date(2025, 12, 31), date(2000, 1, 1)) == 1
    assert month_day_distance(date(2025, 6, 1), date(2000, 6, 4)) == 3


@respx.mock
def test_on_this_day_window_wraps_across_year_boundary(client, monkeypatch):
    use_bundled(monkeypatch, [
        winner(1965, "01-01", "clark"),
        winner(1968, "01-02", "clark_68"),
        winner(1970, "01-04", "too_far"),          # 5 days from 30 Dec
        winner(1990, "12-28", "dec_28"),
        winner(2004, "12-30", "exact"),
        winner(CURRENT_YEAR - 1, "06-01", "unrelated"),
    ])
    mock_current_season([])

    resp = client.get(f"/history/on-this-day?date={CURRENT_YEAR - 1}-12-30&window=3")

    assert resp.status_code == 200
    body = resp.json()
    assert body["date"] == f"{CURRENT_YEAR - 1}-12-30"
    assert body["window"] == 3
    # by month-day distance, then season descending
    assert [i["driver_id"] for i in body["items"]] == ["exact", "dec_28", "clark", "clark_68"]
    clark = body["items"][2]
    assert clark["years_ago"] == CURRENT_YEAR - 1 - 1965
    assert clark["race_name"] == "clark GP"


@respx.mock
def test_on_this_day_merges_current_season_from_jolpica(client, monkeypatch):
    use_bundled(monkeypatch, [winner(CURRENT_YEAR - 1, "03-10", "last_year")])
    route = mock_current_season([{
        "season": str(CURRENT_YEAR), "round": "2", "raceName": "Saudi Arabian Grand Prix",
        "date": f"{CURRENT_YEAR}-03-09",
        "Circuit": {"circuitId": "jeddah", "circuitName": "Jeddah Corniche Circuit",
                    "Location": {"country": "Saudi Arabia"}},
        "Results": [{
            "Driver": {"driverId": "piastri", "givenName": "Oscar", "familyName": "Piastri"},
            "Constructor": {"constructorId": "mclaren", "name": "McLaren"},
        }],
    }])

    resp = client.get(f"/history/on-this-day?date={CURRENT_YEAR}-03-10")
    client.get(f"/history/on-this-day?date={CURRENT_YEAR}-03-11")  # served from cache

    assert resp.status_code == 200
    items = resp.json()["items"]
    assert [i["driver_id"] for i in items] == ["last_year", "piastri"]
    assert items[1] == {
        "season": CURRENT_YEAR, "round": 2, "race_name": "Saudi Arabian Grand Prix",
        "date": f"{CURRENT_YEAR}-03-09", "circuit_id": "jeddah",
        "circuit_name": "Jeddah Corniche Circuit", "country": "Saudi Arabia",
        "driver_id": "piastri", "driver_name": "Oscar Piastri",
        "constructor_id": "mclaren", "constructor_name": "McLaren", "years_ago": 0,
    }
    assert route.call_count == 1


@respx.mock
def test_on_this_day_current_season_failure_still_serves_bundled(client, monkeypatch):
    use_bundled(monkeypatch, [winner(CURRENT_YEAR - 1, "03-10", "last_year")])
    respx.get(f"{JOLPICA_BASE}/{CURRENT_YEAR}/results/1.json").mock(
        return_value=httpx.Response(500)
    )

    resp = client.get(f"/history/on-this-day?date={CURRENT_YEAR}-03-10")

    assert resp.status_code == 200
    assert [i["driver_id"] for i in resp.json()["items"]] == ["last_year"]


@respx.mock
def test_on_this_day_window_max_is_enforced(client, monkeypatch):
    use_bundled(monkeypatch, [winner(CURRENT_YEAR - 1, "03-10", "last_year")])
    mock_current_season([])

    assert client.get("/history/on-this-day?window=8").status_code == 422
    assert client.get("/history/on-this-day?window=-1").status_code == 422
    assert client.get("/history/on-this-day?date=2025-02-30").status_code == 422
    assert client.get("/history/on-this-day?window=7").status_code == 200


def test_bundled_race_winners_file_is_well_formed():
    winners = history.load_race_winners()
    seasons = {w["season"] for w in winners}
    assert min(seasons) == 1950
    assert max(seasons) < CURRENT_YEAR
    assert all(w["date"] and w["driver_id"] and w["constructor_id"] for w in winners)
