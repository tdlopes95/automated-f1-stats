"""
F1 Backend - Session Scheduler
Arms APScheduler jobs based on the race calendar from Jolpica.

Strategy:
  1. On startup: load schedule for current season
  2. For each upcoming session within 14 days:
     - Schedule a "live polling" job starting at session_start
     - Schedule a "fetch final results" job at session_start + offset
     (live polling only when live_polling_enabled, i.e. an OpenF1 token exists)
  3. A daily refresh job keeps the schedule in sync
  4. A daily prune job trims old live snapshots
"""

import logging
from datetime import datetime, timedelta, timezone
from typing import Callable, Optional

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.date import DateTrigger
from apscheduler.triggers.interval import IntervalTrigger
from apscheduler.triggers.cron import CronTrigger

from .jolpica_client import JolpicaClient

logger = logging.getLogger(__name__)

# How long each session type typically lasts (minutes)
SESSION_DURATIONS = {
    "Practice 1": 60,
    "Practice 2": 60,
    "Practice 3": 60,
    "Sprint Qualifying": 30,
    "Sprint": 30,
    "Qualifying": 65,
    "Race": 120,
}

# How many minutes after session END to fetch final results
RESULTS_FETCH_DELAY = {
    "Practice 1": 10,
    "Practice 2": 10,
    "Practice 3": 10,
    "Sprint Qualifying": 15,
    "Sprint": 15,
    "Qualifying": 20,
    "Race": 30,
}

# Polling interval during session (seconds)
LIVE_POLL_INTERVAL = 15

# A results job may run this late (e.g. after a short restart) instead of being skipped
RESULTS_MISFIRE_GRACE = 600


class F1Scheduler:
    def __init__(
        self,
        jolpica: JolpicaClient,
        on_live_poll: Callable,      # async fn(session_name, race_name) called during session
        on_session_ended: Callable,  # async fn(session_name, race_name, round, year) called after session
        live_polling_enabled: bool,  # False -> only results jobs are armed
        on_prune: Optional[Callable] = None,  # async fn() called daily to trim stored snapshots
    ):
        self.jolpica = jolpica
        self.on_live_poll = on_live_poll
        self.on_session_ended = on_session_ended
        self.live_polling_enabled = live_polling_enabled
        self.on_prune = on_prune

        self.scheduler = AsyncIOScheduler(timezone="UTC")
        # session id -> results fetch time; tracks what's already scheduled
        self._armed_sessions: dict[str, datetime] = {}

    async def start(self):
        """Start the scheduler and arm initial jobs."""
        self.scheduler.start()
        mode = "live polling + results" if self.live_polling_enabled else "results only (no OPENF1_TOKEN)"
        logger.info(f"Scheduler started. Mode: {mode}.")

        # Arm jobs for sessions coming up in the next 14 days
        await self.refresh_schedule()

        # Refresh daily (picks up newly announced / moved sessions; schedule is cached)
        self.scheduler.add_job(
            self._refresh_wrapper,
            CronTrigger(hour=6, minute=0),
            id="daily_schedule_refresh",
            replace_existing=True,
        )
        logger.info("Daily schedule refresh armed (06:00 UTC).")

        if self.on_prune:
            self.scheduler.add_job(
                self._prune_wrapper,
                CronTrigger(hour=4, minute=0),
                id="daily_snapshot_prune",
                replace_existing=True,
            )
            logger.info("Daily snapshot prune armed (04:00 UTC).")

    async def _refresh_wrapper(self):
        await self.refresh_schedule()

    async def _prune_wrapper(self):
        try:
            await self.on_prune()
        except Exception as e:
            logger.error(f"Snapshot prune failed: {e}")

    @staticmethod
    def session_id(session: dict) -> str:
        session_dt: datetime = session["session_datetime"]
        name = session["session_name"].replace(" ", "_")
        return f"{session_dt.year}_{session['round']}_{name}"

    @staticmethod
    def results_time(session_dt: datetime, session_name: str) -> datetime:
        duration_min = SESSION_DURATIONS.get(session_name, 90)
        session_end = session_dt + timedelta(minutes=duration_min)
        return session_end + timedelta(minutes=RESULTS_FETCH_DELAY.get(session_name, 20))

    async def refresh_schedule(self):
        """Pull upcoming sessions and arm jobs for any not yet scheduled."""
        logger.info("Refreshing race schedule...")
        now = datetime.now(timezone.utc)
        for session_id in [sid for sid, t in self._armed_sessions.items() if t <= now]:
            del self._armed_sessions[session_id]
        try:
            sessions = await self.jolpica.get_upcoming_sessions(days_ahead=14)
            logger.info(f"Found {len(sessions)} upcoming sessions in next 14 days.")
            for session in sessions:
                await self._arm_session(session)
        except Exception as e:
            logger.error(f"Failed to refresh schedule: {e}")

    async def _arm_session(self, session: dict):
        """
        Arms two jobs per session:
          1. A live polling loop during the session
          2. A one-time "results fetch" job shortly after the session ends
        """
        session_id = self.session_id(session)

        if session_id in self._armed_sessions:
            return   # already armed

        session_dt: datetime = session["session_datetime"]
        session_name: str = session["session_name"]
        race_name: str = session["race_name"]
        round_number: int = session["round"]
        year: int = session_dt.year

        duration_min = SESSION_DURATIONS.get(session_name, 90)
        session_end = session_dt + timedelta(minutes=duration_min)
        results_time = self.results_time(session_dt, session_name)

        now = datetime.now(timezone.utc)

        # ── Job 1: Live polling during session ────────────────────────────────
        if self.live_polling_enabled and session_end > now:
            start_at = max(session_dt, now + timedelta(seconds=5))

            async def make_poll_job(sn=session_name, rn=race_name):
                logger.info(f"[LIVE] Polling {sn} - {rn}")
                try:
                    await self.on_live_poll(sn, rn)
                except Exception as e:
                    logger.error(f"Live poll error for {sn}: {e}")

            self.scheduler.add_job(
                make_poll_job,
                IntervalTrigger(seconds=LIVE_POLL_INTERVAL, start_date=start_at, end_date=session_end),
                id=f"live_poll_{session_id}",
                replace_existing=True,
            )
            logger.info(f"Armed live polling for {session_name} at {session_dt} (ends ~{session_end})")

        # ── Job 2: Fetch final results after session ──────────────────────────
        if results_time > now:
            async def make_results_job(sn=session_name, rn=race_name, rnd=round_number, yr=year):
                logger.info(f"[RESULTS] Fetching final results for {sn} - {rn}")
                try:
                    await self.on_session_ended(sn, rn, rnd, yr)
                except Exception as e:
                    logger.error(f"Results fetch error for {sn}: {e}")

            self.scheduler.add_job(
                make_results_job,
                DateTrigger(run_date=results_time),
                id=f"results_{session_id}",
                replace_existing=True,
                misfire_grace_time=RESULTS_MISFIRE_GRACE,
                coalesce=True,
            )
            logger.info(f"Armed results fetch for {session_name} at {results_time}")

        self._armed_sessions[session_id] = results_time

    def stop(self):
        self.scheduler.shutdown()
        logger.info("Scheduler stopped.")