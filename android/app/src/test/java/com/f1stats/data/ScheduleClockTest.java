package com.f1stats.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.f1stats.DateHelper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleClockTest {

    private static final long MINUTE = 60 * 1000L;
    private static final long HOUR = 60 * MINUTE;

    // Round 1 races Sunday 2026-03-08 04:00 UTC, round 2 races 2026-03-15 07:00 UTC
    private static final List<Map<String, Object>> RACES = Arrays.asList(
            race(2, "2026-03-13T03:30:00Z", "2026-03-14T07:00:00Z", "2026-03-15T07:00:00Z"),
            race(1, "2026-03-06T01:30:00Z", "2026-03-07T05:00:00Z", "2026-03-08T04:00:00Z"));

    private static final long R1_RACE = DateHelper.toMillis("2026-03-08T04:00:00");
    private static final long R2_RACE = DateHelper.toMillis("2026-03-15T07:00:00");
    private static final long R2_QUALI = DateHelper.toMillis("2026-03-14T07:00:00");

    private static Map<String, Object> race(int round, String fp1, String quali, String race) {
        Map<String, Object> r = new HashMap<>();
        r.put("round", (double) round);   // Gson's number type
        List<Map<String, Object>> sessions = new ArrayList<>();
        sessions.add(session("Practice 1", fp1));
        sessions.add(session("Qualifying", quali));
        sessions.add(session("Race", race));
        r.put("sessions", sessions);
        return r;
    }

    private static Map<String, Object> session(String name, String datetime) {
        Map<String, Object> s = new HashMap<>();
        s.put("name", name);
        s.put("datetime", datetime);
        return s;
    }

    private static int roundOf(Map<String, Object> race) {
        return ((Number) race.get("round")).intValue();
    }

    @Test
    public void nextRace_isFirstRoundNotYetFinished_evenOutOfOrder() {
        assertEquals(1, roundOf(ScheduleClock.nextRace(RACES, R1_RACE - 24 * HOUR)));
    }

    @Test
    public void nextRace_staysOnARaceUntilThreeHoursAfterItsStart() {
        assertEquals(1, roundOf(ScheduleClock.nextRace(RACES, R1_RACE + 3 * HOUR - 1)));
        assertEquals(2, roundOf(ScheduleClock.nextRace(RACES, R1_RACE + 3 * HOUR)));
    }

    @Test
    public void nextRace_nullOnceEveryRaceHasFinished() {
        assertNull(ScheduleClock.nextRace(RACES, R2_RACE + 3 * HOUR));
    }

    @Test
    public void liveWindow_opensThirtyMinutesBeforeASession() {
        assertFalse(ScheduleClock.liveWindowOpen(RACES, R2_QUALI - 30 * MINUTE - 1));
        assertTrue(ScheduleClock.liveWindowOpen(RACES, R2_QUALI - 30 * MINUTE));
    }

    @Test
    public void liveWindow_closesFourHoursAfterASessionStarts() {
        assertTrue(ScheduleClock.liveWindowOpen(RACES, R2_QUALI + 4 * HOUR - 1));
        assertFalse(ScheduleClock.liveWindowOpen(RACES, R2_QUALI + 4 * HOUR));
    }

    @Test
    public void liveWindow_closedWithNoSchedule() {
        assertFalse(ScheduleClock.liveWindowOpen(new ArrayList<>(), R2_QUALI));
    }

    @Test
    public void latestRaceSettledAt_isLatestStartedRacePlusThreeHours() {
        assertEquals(-1, ScheduleClock.latestRaceSettledAt(RACES, R1_RACE - 1));
        assertEquals(R1_RACE + 3 * HOUR, ScheduleClock.latestRaceSettledAt(RACES, R2_RACE - 1));
        assertEquals(R2_RACE + 3 * HOUR, ScheduleClock.latestRaceSettledAt(RACES, R2_RACE + HOUR));
    }

    @Test
    public void ttlUntil_shortensTtlForACopyFetchedBeforeTheDeadline() {
        long deadline = R1_RACE + 3 * HOUR;
        assertEquals(5 * MINUTE, ScheduleClock.ttlUntil(deadline - 5 * MINUTE, 15 * MINUTE, deadline));
        // Fetched long before: the plain TTL already expires first
        assertEquals(15 * MINUTE, ScheduleClock.ttlUntil(deadline - HOUR, 15 * MINUTE, deadline));
        // Fetched after the deadline, or no deadline: unchanged
        assertEquals(15 * MINUTE, ScheduleClock.ttlUntil(deadline, 15 * MINUTE, deadline));
        assertEquals(15 * MINUTE, ScheduleClock.ttlUntil(deadline - 5 * MINUTE, 15 * MINUTE, -1));
    }
}
