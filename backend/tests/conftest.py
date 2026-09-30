"""
Shared test fixtures.

The FastAPI app normally wires up its database, HTTP clients and scheduler in a
lifespan handler. Tests deliberately bypass that (the TestClient is used without
its context manager) and instead inject lightweight fakes:

  * a stub database whose reads miss (unless a test seeds standings rows), so
    requests fall through to the HTTP clients
  * real httpx-backed Jolpica / OpenF1 clients, with all network traffic mocked
    by respx (see individual tests)

The slowapi rate limiter is disabled so repeated calls across the suite don't
trip a limit.
"""

import pytest
from fastapi.testclient import TestClient

from app import main
from app.jolpica_client import JolpicaClient
from app.openf1_client import OpenF1Client

JOLPICA_BASE = "https://api.jolpi.ca/ergast/f1"
OPENF1_BASE = "https://api.openf1.org/v1"


class StubDB:
    """
    A database where every write is a no-op and every lookup misses, except
    standings rows that a test seeds via `driver_standings` / `constructor_standings`
    ({year: standings_list}).
    """

    def __init__(self):
        self.driver_standings: dict = {}
        self.constructor_standings: dict = {}
        self.saved_snapshots = 0

    async def get_results(self, *a, **kw):
        return None

    async def get_results_entry(self, *a, **kw):
        return None

    async def save_results(self, *a, **kw):
        return None

    async def get_latest_driver_standings(self, year, *a, **kw):
        return self.driver_standings.get(year)

    async def save_driver_standings(self, *a, **kw):
        return None

    async def get_latest_constructor_standings(self, year, *a, **kw):
        return self.constructor_standings.get(year)

    async def save_constructor_standings(self, *a, **kw):
        return None

    async def get_latest_snapshot_entry(self, *a, **kw):
        return None

    async def save_snapshot(self, *a, **kw):
        self.saved_snapshots += 1

    async def prune_snapshots(self, *a, **kw):
        return 0

    async def upsert_session(self, *a, **kw):
        return None


@pytest.fixture
def stub_db():
    return StubDB()


@pytest.fixture
def client(monkeypatch, stub_db):
    monkeypatch.setattr(main, "db", stub_db)
    monkeypatch.setattr(main, "jolpica", JolpicaClient())
    monkeypatch.setattr(main, "openf1", OpenF1Client())
    monkeypatch.setattr(main.limiter, "enabled", False)
    main._cache.clear()

    # No `with` -> the app's lifespan (real DB, real clients, scheduler) never runs.
    return TestClient(main.app)
