package com.f1stats.data;

import androidx.annotation.Nullable;

import com.f1stats.DateHelper;

import java.util.List;
import java.util.Map;

/**
 * Decisions taken from a stored season schedule instead of a request, kept free of Android so
 * they can be unit tested. Races are schedule maps as /schedule returns them: a "round" and
 * "sessions", each with a "name" and a UTC "datetime".
 */
public final class ScheduleClock {

    private ScheduleClock() {}

    private static final long MINUTE_MS = 60 * 1000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;

    /** How long after its start a race (or any session) counts as finished. */
    public static final long SESSION_LENGTH_MS = 3 * HOUR_MS;
    /** A session that started less than this ago may still be live (red flags, delays). */
    public static final long LIVE_AFTER_START_MS = 4 * HOUR_MS;
    /** How early before a session's start the live badge may show. */
    public static final long LIVE_BEFORE_START_MS = 30 * MINUTE_MS;

    /**
     * The first race (in round order) whose Race session hasn't finished at {@code now}, or
     * null when every race has (offseason). A race without a known Race time is skipped.
     */
    @Nullable
    public static Map<String, Object> nextRace(List<Map<String, Object>> races, long now) {
        Map<String, Object> next = null;
        int nextRound = Integer.MAX_VALUE;
        for (Map<String, Object> race : races) {
            long start = sessionStart(race, "Race");
            if (start < 0 || start + SESSION_LENGTH_MS <= now) continue;
            int round = round(race);
            if (round < nextRound) {
                next = race;
                nextRound = round;
            }
        }
        return next;
    }

    /**
     * Whether any session started less than 4 hours before {@code now} or starts within the
     * next 30 minutes: the only time /live can have something worth a badge.
     */
    public static boolean liveWindowOpen(List<Map<String, Object>> races, long now) {
        for (Map<String, Object> race : races) {
            Object sessions = race.get("sessions");
            if (!(sessions instanceof List)) continue;
            for (Object s : (List<?>) sessions) {
                if (!(s instanceof Map)) continue;
                long start = toMillis(((Map<?, ?>) s).get("datetime"));
                if (start < 0) continue;
                if (start - LIVE_BEFORE_START_MS <= now && now < start + LIVE_AFTER_START_MS) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * When the latest race's results settle: the most recent Race start at or before
     * {@code now}, plus 3 hours. -1 if no race has started.
     */
    public static long latestRaceSettledAt(List<Map<String, Object>> races, long now) {
        long latest = -1;
        for (Map<String, Object> race : races) {
            long start = sessionStart(race, "Race");
            if (start >= 0 && start <= now && start > latest) latest = start;
        }
        return latest < 0 ? -1 : latest + SESSION_LENGTH_MS;
    }

    /**
     * {@code ttlMs}, shortened so that a copy fetched before {@code deadline} goes stale once
     * the deadline passes. A deadline of -1 (none) leaves the TTL alone.
     */
    public static long ttlUntil(long fetchedAt, long ttlMs, long deadline) {
        if (deadline < 0 || fetchedAt >= deadline) return ttlMs;
        return Math.min(ttlMs, deadline - fetchedAt);
    }

    /** UTC start of the named session, or -1 if the race doesn't have it. */
    public static long sessionStart(Map<String, Object> race, String name) {
        Object sessions = race.get("sessions");
        if (!(sessions instanceof List)) return -1;
        for (Object s : (List<?>) sessions) {
            if (s instanceof Map && name.equals(((Map<?, ?>) s).get("name"))) {
                return toMillis(((Map<?, ?>) s).get("datetime"));
            }
        }
        return -1;
    }

    private static long toMillis(@Nullable Object datetime) {
        if (datetime == null) return -1;
        long t = DateHelper.toMillis(datetime.toString());
        return t > 0 ? t : -1;
    }

    private static int round(Map<String, Object> race) {
        Object r = race.get("round");
        if (r instanceof Number) return ((Number) r).intValue();
        try {
            return r != null ? Integer.parseInt(r.toString()) : Integer.MAX_VALUE;
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
