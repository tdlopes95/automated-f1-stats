package com.f1stats.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.f1stats.notifications.ReminderPlanner.SessionKind;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** Notification preferences, plus the bookkeeping the scheduler and worker need. */
public class NotificationSettings {

    private static final String PREFS_NAME = "notification_prefs";
    private static final String KEY_REMINDERS = "reminders_enabled";
    private static final String KEY_LEAD_MINUTES = "reminder_lead_minutes";
    private static final String KEY_SESSIONS = "reminder_sessions";
    private static final String KEY_RESULTS = "results_enabled";
    private static final String KEY_NOTIFIED = "results_notified";
    private static final String KEY_ALARM_CODES = "scheduled_alarm_codes";

    public static final int[] LEAD_MINUTE_OPTIONS = {15, 30, 60};
    public static final int DEFAULT_LEAD_MINUTES = 15;

    private static final Set<SessionKind> DEFAULT_SESSIONS = EnumSet.of(
            SessionKind.QUALIFYING, SessionKind.SPRINT_QUALIFYING,
            SessionKind.SPRINT, SessionKind.RACE);

    private static NotificationSettings instance;
    private final SharedPreferences prefs;

    private NotificationSettings(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized NotificationSettings getInstance(Context context) {
        if (instance == null) instance = new NotificationSettings(context);
        return instance;
    }

    // ── Reminders ─────────────────────────────────────────────────────────────

    public boolean isRemindersEnabled() {
        return prefs.getBoolean(KEY_REMINDERS, false);
    }

    public void setRemindersEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REMINDERS, enabled).apply();
    }

    public int getLeadMinutes() {
        int stored = prefs.getInt(KEY_LEAD_MINUTES, DEFAULT_LEAD_MINUTES);
        for (int option : LEAD_MINUTE_OPTIONS) {
            if (option == stored) return stored;
        }
        return DEFAULT_LEAD_MINUTES;
    }

    public void setLeadMinutes(int minutes) {
        prefs.edit().putInt(KEY_LEAD_MINUTES, minutes).apply();
    }

    @NonNull
    public Set<SessionKind> getSessions() {
        Set<String> stored = prefs.getStringSet(KEY_SESSIONS, null);
        if (stored == null) return EnumSet.copyOf(DEFAULT_SESSIONS);
        Set<SessionKind> out = EnumSet.noneOf(SessionKind.class);
        for (String name : stored) {
            try {
                out.add(SessionKind.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // A kind removed in a later version
            }
        }
        return out;
    }

    public void setSessionEnabled(@NonNull SessionKind kind, boolean enabled) {
        Set<SessionKind> sessions = getSessions();
        if (enabled) sessions.add(kind);
        else sessions.remove(kind);
        Set<String> names = new HashSet<>();
        for (SessionKind k : sessions) names.add(k.name());
        prefs.edit().putStringSet(KEY_SESSIONS, names).apply();
    }

    // ── Favourite results ─────────────────────────────────────────────────────

    public boolean isResultsEnabled() {
        return prefs.getBoolean(KEY_RESULTS, false);
    }

    public void setResultsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_RESULTS, enabled).apply();
    }

    /** Session keys ({@link ReminderPlanner#key}) whose results were already handled. */
    @NonNull
    public synchronized Set<String> getNotifiedKeys() {
        return new HashSet<>(prefs.getStringSet(KEY_NOTIFIED, new HashSet<>()));
    }

    /** Remembers a handled session; keys from before last season are pruned. */
    public synchronized void markNotified(@NonNull String key, int currentYear) {
        Set<String> keys = new HashSet<>();
        for (String k : getNotifiedKeys()) {
            int slash = k.indexOf('/');
            try {
                if (slash > 0 && Integer.parseInt(k.substring(0, slash)) >= currentYear - 1) keys.add(k);
            } catch (NumberFormatException ignored) {
                // Malformed key: drop it
            }
        }
        keys.add(key);
        prefs.edit().putStringSet(KEY_NOTIFIED, keys).apply();
    }

    // ── Scheduler bookkeeping ─────────────────────────────────────────────────

    /** Request codes of the alarms currently set, so they can be cancelled later. */
    @NonNull
    Set<Integer> getScheduledAlarmCodes() {
        Set<Integer> out = new HashSet<>();
        for (String s : prefs.getStringSet(KEY_ALARM_CODES, new HashSet<>())) {
            try {
                out.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
                // Skip
            }
        }
        return out;
    }

    void setScheduledAlarmCodes(@NonNull Set<Integer> codes) {
        Set<String> out = new HashSet<>();
        for (int code : codes) out.add(String.valueOf(code));
        prefs.edit().putStringSet(KEY_ALARM_CODES, out).apply();
    }
}
