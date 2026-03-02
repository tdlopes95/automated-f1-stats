"""
F1 Backend - Session Scheduler
Arms APScheduler jobs based on the race calendar from Jolpica.

Strategy:
  1. On startup: load schedule for current season
  2. For each upcoming session within 14 days:
     - Schedule a "live polling" job starting at session_start
     - Schedule a "fetch final results" job at session_start + offset
  3. A weekly refresh job keeps the schedule in sync
"""

import asyncio
import logging
from datetime import datetime, timedelta
from typing import Callable

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


class F1Scheduler:
    def __init__(
        self,
        jolpica: JolpicaClient,
        on_live_poll: Callable,      # async fn(session_name, race_name) called during session
        on_session_ended: Callable,  # async fn(session_name, race_name, round) called after session
    ):
        self.jolpica = jolpica
        self.on_live_poll = on_live_poll
        self.on_session_ended = on_session_ended

        self.scheduler = AsyncIOScheduler(timezone="UTC")
        self._armed_sessions: set[str] = set()   # track what's already scheduled

    async def start(self):
        """Start the scheduler and arm initial jobs."""
        self.scheduler.start()
        logger.info("Scheduler started.")

        # Arm jobs for sessions coming up in the next 14 days
        await self.refresh_schedule()

        # Refresh schedule every week (picks up newly announced sessions)
        self.scheduler.add_job(
            self._refresh_wrapper,
            CronTrigger(day_of_week="mon", hour=6, minute=0),
            id="weekly_schedule_refresh",
            replace_existing=True,
        )
        logger.info("Weekly schedule refresh armed.")

    async def _refresh_wrapper(self):
        await self.refresh_schedule()

    async def refresh_schedule(self):
        """Pull upcoming sessions and arm jobs for any not yet scheduled."""
        logger.info("Refreshing race schedule...")
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
        session_id = f"{session['round']}_{session['session_name'].replace(' ', '_')}"

        if session_id in self._armed_sessions:
            return   # already armed

        session_dt: datetime = session["session_datetime"]
        session_name: str = session["session_name"]
        race_name: str = session["race_name"]
        round_number: int = session["round"]

        duration_min = SESSION_DURATIONS.get(session_name, 90)
        session_end = session_dt + timedelta(minutes=duration_min)
        results_time = session_end + timedelta(minutes=RESULTS_FETCH_DELAY.get(session_name, 20))

        now = datetime.utcnow()

        # ── Job 1: Live polling during session ────────────────────────────────
        if session_end > now:
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
            async def make_results_job(sn=session_name, rn=race_name, rnd=round_number):
                logger.info(f"[RESULTS] Fetching final results for {sn} - {rn}")
                try:
                    await self.on_session_ended(sn, rn, rnd)
                except Exception as e:
                    logger.error(f"Results fetch error for {sn}: {e}")

            self.scheduler.add_job(
                make_results_job,
                DateTrigger(run_date=results_time),
                id=f"results_{session_id}",
                replace_existing=True,
            )
            logger.info(f"Armed results fetch for {session_name} at {results_time}")

        self._armed_sessions.add(session_id)

    def stop(self):
        self.scheduler.shutdown()
        logger.info("Scheduler stopped.")