"""
F1 Backend - Database Layer
SQLite via aiosqlite. Simple key-value style storage for session results.
Easy to swap to PostgreSQL later by changing the driver.
"""

import json
import logging
from datetime import datetime
from typing import Any, Optional

import aiosqlite

logger = logging.getLogger(__name__)

DB_PATH = "f1_data.db"


class Database:
    def __init__(self, path: str = DB_PATH):
        self.path = path
        self._db: Optional[aiosqlite.Connection] = None

    async def connect(self):
        self._db = await aiosqlite.connect(self.path)
        self._db.row_factory = aiosqlite.Row
        await self._create_tables()
        logger.info(f"Database connected: {self.path}")

    async def close(self):
        if self._db:
            await self._db.close()

    async def _create_tables(self):
        await self._db.executescript("""
            CREATE TABLE IF NOT EXISTS sessions_cache (
                session_key     INTEGER PRIMARY KEY,
                session_name    TEXT,
                session_type    TEXT,
                date_start      TEXT,
                date_end        TEXT,
                location        TEXT,
                country_name    TEXT,
                circuit_name    TEXT,
                year            INTEGER,
                raw_json        TEXT
            );

            CREATE TABLE IF NOT EXISTS race_results (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                year            INTEGER NOT NULL,
                round           INTEGER NOT NULL,
                session_type    TEXT NOT NULL,   -- "Race", "Qualifying", "Sprint"
                fetched_at      TEXT NOT NULL,
                results_json    TEXT NOT NULL,
                UNIQUE(year, round, session_type)
            );

            CREATE TABLE IF NOT EXISTS live_snapshots (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                session_key     INTEGER NOT NULL,
                captured_at     TEXT NOT NULL,
                snapshot_json   TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS driver_standings (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                year            INTEGER NOT NULL,
                round           INTEGER,
                fetched_at      TEXT NOT NULL,
                standings_json  TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS constructor_standings (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                year            INTEGER NOT NULL,
                round           INTEGER,
                fetched_at      TEXT NOT NULL,
                standings_json  TEXT NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_live_snapshots_session
                ON live_snapshots(session_key, captured_at DESC);
        """)
        await self._db.commit()

    # ── Sessions ──────────────────────────────────────────────────────────────

    async def upsert_session(self, session: dict):
        await self._db.execute("""
            INSERT INTO sessions_cache
                (session_key, session_name, session_type, date_start, date_end,
                 location, country_name, circuit_name, year, raw_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(session_key) DO UPDATE SET
                session_name=excluded.session_name,
                date_end=excluded.date_end,
                raw_json=excluded.raw_json
        """, (
            session.get("session_key"),
            session.get("session_name"),
            session.get("session_type"),
            session.get("date_start"),
            session.get("date_end"),
            session.get("location"),
            session.get("country_name"),
            session.get("circuit_short_name"),
            session.get("year"),
            json.dumps(session),
        ))
        await self._db.commit()

    # ── Results ───────────────────────────────────────────────────────────────

    async def save_results(self, year: int, round_number: int, session_type: str, results: list):
        await self._db.execute("""
            INSERT INTO race_results (year, round, session_type, fetched_at, results_json)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(year, round, session_type) DO UPDATE SET
                fetched_at=excluded.fetched_at,
                results_json=excluded.results_json
        """, (year, round_number, session_type, datetime.utcnow().isoformat(), json.dumps(results)))
        await self._db.commit()
        logger.info(f"Saved {session_type} results for {year} R{round_number}")

    async def get_results(self, year: int, round_number: int, session_type: str) -> Optional[list]:
        async with self._db.execute("""
            SELECT results_json FROM race_results
            WHERE year=? AND round=? AND session_type=?
        """, (year, round_number, session_type)) as cursor:
            row = await cursor.fetchone()
            return json.loads(row["results_json"]) if row else None

    async def get_latest_results(self, session_type: str = "Race") -> Optional[dict]:
        """Get the most recently stored results of a given type."""
        async with self._db.execute("""
            SELECT year, round, results_json FROM race_results
            WHERE session_type=?
            ORDER BY year DESC, round DESC
            LIMIT 1
        """, (session_type,)) as cursor:
            row = await cursor.fetchone()
            if not row:
                return None
            return {
                "year": row["year"],
                "round": row["round"],
                "session_type": session_type,
                "results": json.loads(row["results_json"]),
            }

    # ── Live Snapshots ────────────────────────────────────────────────────────

    async def save_snapshot(self, session_key: int, snapshot: dict):
        await self._db.execute("""
            INSERT INTO live_snapshots (session_key, captured_at, snapshot_json)
            VALUES (?, ?, ?)
        """, (session_key, datetime.utcnow().isoformat(), json.dumps(snapshot)))
        await self._db.commit()

    async def get_latest_snapshot(self, session_key: int) -> Optional[dict]:
        async with self._db.execute("""
            SELECT snapshot_json FROM live_snapshots
            WHERE session_key=?
            ORDER BY captured_at DESC
            LIMIT 1
        """, (session_key,)) as cursor:
            row = await cursor.fetchone()
            return json.loads(row["snapshot_json"]) if row else None

    # ── Standings ─────────────────────────────────────────────────────────────

    async def save_driver_standings(self, year: int, round_number: Optional[int], standings: list):
        await self._db.execute("""
            INSERT INTO driver_standings (year, round, fetched_at, standings_json)
            VALUES (?, ?, ?, ?)
        """, (year, round_number, datetime.utcnow().isoformat(), json.dumps(standings)))
        await self._db.commit()

    async def get_latest_driver_standings(self) -> Optional[list]:
        async with self._db.execute("""
            SELECT standings_json FROM driver_standings
            ORDER BY year DESC, round DESC, fetched_at DESC
            LIMIT 1
        """) as cursor:
            row = await cursor.fetchone()
            return json.loads(row["standings_json"]) if row else None

    async def save_constructor_standings(self, year: int, round_number: Optional[int], standings: list):
        await self._db.execute("""
            INSERT INTO constructor_standings (year, round, fetched_at, standings_json)
            VALUES (?, ?, ?, ?)
        """, (year, round_number, datetime.utcnow().isoformat(), json.dumps(standings)))
        await self._db.commit()

    async def get_latest_constructor_standings(self) -> Optional[list]:
        async with self._db.execute("""
            SELECT standings_json FROM constructor_standings
            ORDER BY year DESC, round DESC, fetched_at DESC
            LIMIT 1
        """) as cursor:
            row = await cursor.fetchone()
            return json.loads(row["standings_json"]) if row else None