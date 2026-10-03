package com.f1stats.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.DateHelper;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure planning logic for session reminders and result checks: which sessions get an alarm,
 * when, and which Race/Sprint results still need checking. No Android dependencies.
 */
public final class ReminderPlanner {

    private ReminderPlanner() {}

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;

    /** Only sessions starting within this window get an alarm. */
    static final long REMINDER_HORIZON_MS = 14 * 24 * HOUR_MS;
    /** Results are first checked this long after the session starts. */
    static final long RESULTS_DELAY_MS = 150 * MINUTE_MS;
    /** A missed check is still run (with no delay) until this long after the session starts. */
    static final long RESULTS_CATCH_UP_MS = 24 * HOUR_MS;
    /** The worker stops retrying empty results this long after the session starts. */
    static final long RESULTS_GIVE_UP_MS = 8 * HOUR_MS;

    /** Reminder categories the user can pick in Settings. */
    public enum SessionKind {
        PRACTICE, QUALIFYING, SPRINT_QUALIFYING, SPRINT, RACE;

        /** Maps a schedule session name ("Practice 2", "Sprint Qualifying", …) to its kind. */
        @Nullable
        public static SessionKind fromName(@Nullable String name) {
            if (name == null) return null;
            switch (name) {
                case "Race": return RACE;
                case "Qualifying": return QUALIFYING;
                case "Sprint": return SPRINT;
                case "Sprint Qualifying":
                case "Sprint Shootout": return SPRINT_QUALIFYING;
                default: return name.startsWith("Practice") ? PRACTICE : null;
            }
        }
    }

    /** One session from the schedule. */
    public static final class Session {
        public final int year;
        public final int round;
        @NonNull public final String raceName;
        @NonNull public final String name;
        @NonNull public final SessionKind kind;
        public final long startMillis;
        @Nullable public final String circuit;
        @Nullable public final String circuitId;
        public final boolean hasSprint;

        public Session(int year, int round, @NonNull String raceName, @NonNull String name,
                       @NonNull SessionKind kind, long startMillis, @Nullable String circuit,
                       @Nullable String circuitId, boolean hasSprint) {
            this.year = year;
            this.round = round;
            this.raceName = raceName;
            this.name = name;
            this.kind = kind;
            this.startMillis = startMillis;
            this.circuit = circuit;
            this.circuitId = circuitId;
            this.hasSprint = hasSprint;
        }

        /** Stable identity of this session, e.g. "2026/18/Race". */
        @NonNull
        public String key() {
            return ReminderPlanner.key(year, round, name);
        }

        /** Stable alarm request code / notification id for this session. */
        public int requestCode() {
            return ReminderPlanner.requestCode(year, round, name);
        }
    }

    /** An alarm to set. */
    public static final class Reminder {
        @NonNull public final Session session;
        public final long triggerAtMillis;

        Reminder(@NonNull Session session, long triggerAtMillis) {
            this.session = session;
            this.triggerAtMillis = triggerAtMillis;
        }
    }

    /** A results check to enqueue. */
    public static final class ResultCheck {
        @NonNull public final Session session;
        public final long delayMillis;

        ResultCheck(@NonNull Session session, long delayMillis) {
            this.session = session;
            this.delayMillis = delayMillis;
        }
    }

    @NonNull
    public static String key(int year, int round, @NonNull String sessionName) {
        return year + "/" + round + "/" + sessionName;
    }

    /**
     * Stable and unique per (year, round, session). The session's position in the weekend
     * keeps codes apart; unknown names hash into the spare slots.
     */
    public static int requestCode(int year, int round, @NonNull String sessionName) {
        int slot;
        switch (sessionName) {
            case "Practice 1": slot = 1; break;
            case "Practice 2": slot = 2; break;
            case "Practice 3": slot = 3; break;
            case "Sprint Qualifying":
            case "Sprint Shootout": slot = 4; break;
            case "Sprint": slot = 5; break;
            case "Qualifying": slot = 6; break;
            case "Race": slot = 7; break;
            default: slot = 8 + Math.floorMod(sessionName.hashCode(), 2); break;
        }
        return year * 10_000 + round * 10 + slot;
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    /** The sessions of one cached schedule row (the backend's race JSON). */
    @NonNull
    public static List<Session> parseRound(int year, int round, @Nullable String raceName,
                                           @Nullable String raceJson) {
        List<Session> out = new ArrayList<>();
        if (raceJson == null) return out;
        Map<String, Object> race;
        try {
            race = GSON.fromJson(raceJson, MAP_TYPE);
        } catch (JsonParseException e) {
            return out;
        }
        if (race == null || !(race.get("sessions") instanceof List)) return out;

        String name = raceName != null ? raceName : str(race.get("race_name"));
        if (name == null) name = "";
        String circuit = str(race.get("circuit"));
        String circuitId = str(race.get("circuit_id"));
        List<?> sessions = (List<?>) race.get("sessions");

        boolean hasSprint = false;
        for (Object s : sessions) {
            if (s instanceof Map && "Sprint".equals(((Map<?, ?>) s).get("name"))) hasSprint = true;
        }
        for (Object s : sessions) {
            if (!(s instanceof Map)) continue;
            String sessionName = str(((Map<?, ?>) s).get("name"));
            SessionKind kind = SessionKind.fromName(sessionName);
            long start = DateHelper.toMillis(str(((Map<?, ?>) s).get("datetime")));
            if (kind == null || start <= 0) continue;
            out.add(new Session(year, round, name, sessionName, kind, start,
                    circuit, circuitId, hasSprint));
        }
        return out;
    }

    // ── Planning ──────────────────────────────────────────────────────────────

    /**
     * Alarms for every enabled session starting within the next 14 days, at start minus the
     * lead time. Sessions whose reminder time has already passed are skipped, so rescheduling
     * never re-fires a reminder.
     */
    @NonNull
    public static List<Reminder> planReminders(@NonNull Collection<Session> sessions,
                                               @NonNull Set<SessionKind> enabled,
                                               int leadMinutes, long now) {
        List<Reminder> out = new ArrayList<>();
        for (Session s : sessions) {
            if (!enabled.contains(s.kind)) continue;
            if (s.startMillis > now + REMINDER_HORIZON_MS) continue;
            long trigger = s.startMillis - leadMinutes * MINUTE_MS;
            if (trigger <= now) continue;
            out.add(new Reminder(s, trigger));
        }
        return out;
    }

    /**
     * Results checks for Race and Sprint sessions: upcoming ones (within the reminder window)
     * are delayed until start + 2.5h; one already past that point but within 24h of its start,
     * and not yet notified, runs straight away.
     */
    @NonNull
    public static List<ResultCheck> planResultChecks(@NonNull Collection<Session> sessions,
                                                     @NonNull Set<String> notifiedKeys, long now) {
        List<ResultCheck> out = new ArrayList<>();
        for (Session s : sessions) {
            if (s.kind != SessionKind.RACE && s.kind != SessionKind.SPRINT) continue;
            if (notifiedKeys.contains(s.key())) continue;
            long checkAt = s.startMillis + RESULTS_DELAY_MS;
            if (now < checkAt) {
                if (s.startMillis <= now + REMINDER_HORIZON_MS) {
                    out.add(new ResultCheck(s, checkAt - now));
                }
            } else if (now < s.startMillis + RESULTS_CATCH_UP_MS) {
                out.add(new ResultCheck(s, 0));
            }
        }
        return out;
    }

    /** True once empty results should no longer be retried. */
    public static boolean shouldGiveUp(long sessionStartMillis, long now) {
        return now > sessionStartMillis + RESULTS_GIVE_UP_MS;
    }

    /** The first session starting after {@code now}, or null. */
    @Nullable
    public static Session nextSession(@NonNull Collection<Session> sessions, long now) {
        Session best = null;
        for (Session s : sessions) {
            if (s.startMillis > now && (best == null || s.startMillis < best.startMillis)) best = s;
        }
        return best;
    }

    /** The most recent Race whose results should be in (started more than 2.5h ago), or null. */
    @Nullable
    public static Session lastCompletedRace(@NonNull Collection<Session> sessions, long now) {
        Session best = null;
        for (Session s : sessions) {
            if (s.kind != SessionKind.RACE || s.startMillis + RESULTS_DELAY_MS > now) continue;
            if (best == null || s.startMillis > best.startMillis) best = s;
        }
        return best;
    }

    @Nullable
    private static String str(@Nullable Object o) {
        return o != null ? o.toString() : null;
    }
}
