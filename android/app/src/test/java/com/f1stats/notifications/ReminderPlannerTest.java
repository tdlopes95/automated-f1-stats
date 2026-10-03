package com.f1stats.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.f1stats.notifications.ReminderPlanner.Reminder;
import com.f1stats.notifications.ReminderPlanner.ResultCheck;
import com.f1stats.notifications.ReminderPlanner.Session;
import com.f1stats.notifications.ReminderPlanner.SessionKind;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReminderPlannerTest {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    // 2026-10-01T12:00:00Z
    private static final long NOW = 1_790_856_000_000L;

    private static final Set<SessionKind> DEFAULTS = EnumSet.of(SessionKind.QUALIFYING,
            SessionKind.SPRINT_QUALIFYING, SessionKind.SPRINT, SessionKind.RACE);

    /** A sprint weekend: FP1 Fri, SQ Fri, Sprint + Quali Sat, Race Sun, starting in 1 day. */
    private static final String SPRINT_WEEKEND = "{\"round\": 19, \"race_name\": \"United States Grand Prix\","
            + "\"circuit\": \"Circuit of the Americas\", \"circuit_id\": \"americas\", \"sessions\": ["
            + "{\"name\": \"Practice 1\", \"datetime\": \"2026-10-02T17:30:00+00:00\"},"
            + "{\"name\": \"Sprint Qualifying\", \"datetime\": \"2026-10-02T21:30:00+00:00\"},"
            + "{\"name\": \"Sprint\", \"datetime\": \"2026-10-03T17:00:00+00:00\"},"
            + "{\"name\": \"Qualifying\", \"datetime\": \"2026-10-03T21:00:00+00:00\"},"
            + "{\"name\": \"Race\", \"datetime\": \"2026-10-04T19:00:00+00:00\"}]}";

    private static List<Session> weekend() {
        return ReminderPlanner.parseRound(2026, 19, "United States Grand Prix", SPRINT_WEEKEND);
    }

    private static List<String> names(List<Reminder> reminders) {
        List<String> out = new ArrayList<>();
        for (Reminder r : reminders) out.add(r.session.name);
        return out;
    }

    private static Session session(SessionKind kind, String name, long start) {
        return new Session(2026, 18, "Singapore Grand Prix", name, kind, start,
                "Marina Bay", "marina_bay", false);
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    @Test
    public void parsesEverySessionWithKindAndSprintFlag() {
        List<Session> sessions = weekend();
        assertEquals(5, sessions.size());
        assertEquals(SessionKind.PRACTICE, sessions.get(0).kind);
        assertEquals(SessionKind.SPRINT_QUALIFYING, sessions.get(1).kind);
        assertEquals(SessionKind.RACE, sessions.get(4).kind);
        assertTrue(sessions.get(4).hasSprint);
        assertEquals("americas", sessions.get(4).circuitId);
        assertEquals("2026/19/Race", sessions.get(4).key());
    }

    @Test
    public void malformedJsonGivesNoSessions() {
        assertTrue(ReminderPlanner.parseRound(2026, 1, "X", "not json").isEmpty());
        assertTrue(ReminderPlanner.parseRound(2026, 1, "X", null).isEmpty());
        assertTrue(ReminderPlanner.parseRound(2026, 1, "X", "{\"round\": 1}").isEmpty());
    }

    // ── Reminders ─────────────────────────────────────────────────────────────

    @Test
    public void defaultSettingsSkipPractice() {
        List<Reminder> reminders = ReminderPlanner.planReminders(weekend(), DEFAULTS, 15, NOW);
        assertEquals(List.of("Sprint Qualifying", "Sprint", "Qualifying", "Race"), names(reminders));
    }

    @Test
    public void onlySelectedSessionsAreScheduled() {
        List<Reminder> reminders = ReminderPlanner.planReminders(weekend(),
                EnumSet.of(SessionKind.PRACTICE, SessionKind.RACE), 15, NOW);
        assertEquals(List.of("Practice 1", "Race"), names(reminders));
    }

    @Test
    public void noSessionsSelectedSchedulesNothing() {
        assertTrue(ReminderPlanner.planReminders(weekend(),
                EnumSet.noneOf(SessionKind.class), 15, NOW).isEmpty());
    }

    @Test
    public void triggerIsStartMinusLeadTime() {
        Session race = session(SessionKind.RACE, "Race", NOW + DAY);
        for (int lead : new int[] {15, 30, 60}) {
            List<Reminder> r = ReminderPlanner.planReminders(List.of(race), DEFAULTS, lead, NOW);
            assertEquals(NOW + DAY - lead * MINUTE, r.get(0).triggerAtMillis);
        }
    }

    @Test
    public void onlyTheNextFourteenDays() {
        Session inside = session(SessionKind.RACE, "Race", NOW + 14 * DAY);
        Session outside = session(SessionKind.QUALIFYING, "Qualifying", NOW + 14 * DAY + MINUTE);
        List<Reminder> r = ReminderPlanner.planReminders(List.of(inside, outside), DEFAULTS, 15, NOW);
        assertEquals(List.of("Race"), names(r));
    }

    @Test
    public void pastOrDueRemindersAreSkipped() {
        Session started = session(SessionKind.RACE, "Race", NOW - HOUR);
        Session withinLead = session(SessionKind.QUALIFYING, "Qualifying", NOW + 10 * MINUTE);
        Session exactlyDue = session(SessionKind.SPRINT, "Sprint", NOW + 15 * MINUTE);
        assertTrue(ReminderPlanner.planReminders(List.of(started, withinLead, exactlyDue),
                DEFAULTS, 15, NOW).isEmpty());
    }

    @Test
    public void requestCodesAreStableAndUnique() {
        Set<Integer> codes = new HashSet<>();
        for (Session s : weekend()) codes.add(s.requestCode());
        assertEquals(5, codes.size());
        assertEquals(ReminderPlanner.requestCode(2026, 19, "Race"), weekend().get(4).requestCode());
        assertNotEquals(ReminderPlanner.requestCode(2026, 1, "Race"),
                ReminderPlanner.requestCode(2025, 1, "Race"));
        assertNotEquals(ReminderPlanner.requestCode(2026, 1, "Race"),
                ReminderPlanner.requestCode(2026, 2, "Race"));
    }

    // ── Result checks ─────────────────────────────────────────────────────────

    @Test
    public void upcomingRaceAndSprintAreDelayedToStartPlusTwoAndAHalfHours() {
        List<ResultCheck> checks = ReminderPlanner.planResultChecks(weekend(),
                Collections.emptySet(), NOW);
        assertEquals(2, checks.size());
        for (ResultCheck c : checks) {
            assertTrue(c.session.kind == SessionKind.RACE || c.session.kind == SessionKind.SPRINT);
            assertEquals(c.session.startMillis + 150 * MINUTE, NOW + c.delayMillis);
        }
    }

    @Test
    public void catchUpRunsImmediatelyWithin24Hours() {
        Session race = session(SessionKind.RACE, "Race", NOW - 5 * HOUR);
        List<ResultCheck> checks = ReminderPlanner.planResultChecks(List.of(race),
                Collections.emptySet(), NOW);
        assertEquals(1, checks.size());
        assertEquals(0, checks.get(0).delayMillis);
    }

    @Test
    public void catchUpBoundaries() {
        // Just before start + 2.5h: still a delayed check
        Session soon = session(SessionKind.RACE, "Race", NOW - 150 * MINUTE + MINUTE);
        assertEquals(MINUTE, ReminderPlanner.planResultChecks(List.of(soon),
                Collections.emptySet(), NOW).get(0).delayMillis);
        // Exactly start + 2.5h: immediate
        Session due = session(SessionKind.RACE, "Race", NOW - 150 * MINUTE);
        assertEquals(0, ReminderPlanner.planResultChecks(List.of(due),
                Collections.emptySet(), NOW).get(0).delayMillis);
        // 24h after start: too late
        Session old = session(SessionKind.RACE, "Race", NOW - DAY);
        assertTrue(ReminderPlanner.planResultChecks(List.of(old),
                Collections.emptySet(), NOW).isEmpty());
    }

    @Test
    public void alreadyNotifiedSessionsAreNotCaughtUp() {
        Session race = session(SessionKind.RACE, "Race", NOW - 5 * HOUR);
        assertTrue(ReminderPlanner.planResultChecks(List.of(race),
                Set.of(race.key()), NOW).isEmpty());
    }

    @Test
    public void qualifyingAndPracticeNeverGetResultChecks() {
        Session quali = session(SessionKind.QUALIFYING, "Qualifying", NOW - 5 * HOUR);
        Session fp1 = session(SessionKind.PRACTICE, "Practice 1", NOW + HOUR);
        assertTrue(ReminderPlanner.planResultChecks(List.of(quali, fp1),
                Collections.emptySet(), NOW).isEmpty());
    }

    @Test
    public void giveUpAfterEightHours() {
        long start = NOW - 8 * HOUR;
        assertTrue(!ReminderPlanner.shouldGiveUp(start, NOW));
        assertTrue(ReminderPlanner.shouldGiveUp(start, NOW + 1));
    }

    @Test
    public void lastCompletedRaceIgnoresRacesStillRunning() {
        Session done = session(SessionKind.RACE, "Race", NOW - 7 * DAY);
        Session running = new Session(2026, 19, "United States Grand Prix", "Race",
                SessionKind.RACE, NOW - HOUR, null, null, true);
        assertEquals(done, ReminderPlanner.lastCompletedRace(List.of(done, running), NOW));
    }
}
