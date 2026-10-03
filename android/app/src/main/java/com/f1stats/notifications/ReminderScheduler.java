package com.f1stats.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.work.WorkManager;

import com.f1stats.SeasonHelper;
import com.f1stats.db.AppDatabase;
import com.f1stats.db.CachedSchedule;
import com.f1stats.notifications.ReminderPlanner.Reminder;
import com.f1stats.notifications.ReminderPlanner.ResultCheck;
import com.f1stats.notifications.ReminderPlanner.Session;
import com.f1stats.util.DebugLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Sets session reminder alarms and enqueues favourite result checks from the Room schedule.
 * Rescheduling is idempotent: it cancels every alarm it set last time, then sets the current
 * plan. Called on app start, schedule refresh, settings change and the system broadcasts in
 * {@link ReminderReceiver}.
 */
public final class ReminderScheduler {

    private ReminderScheduler() {}

    private static final String TAG = "ReminderScheduler";
    private static final long WINDOW_MS = 5 * 60_000L;

    static final String EXTRA_YEAR = "year";
    static final String EXTRA_ROUND = "round";
    static final String EXTRA_SESSION = "session";
    static final String EXTRA_RACE_NAME = "race_name";
    static final String EXTRA_START = "start";

    // Serialises reschedules so two callers never interleave cancel/set
    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

    public static void rescheduleAsync(@NonNull Context context) {
        rescheduleAsync(context, null);
    }

    /** {@code onDone} runs on the background thread once rescheduling finishes. */
    public static void rescheduleAsync(@NonNull Context context, @Nullable Runnable onDone) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                reschedule(app);
            } catch (RuntimeException e) {
                DebugLog.d(TAG, "reschedule failed: " + e);
            } finally {
                if (onDone != null) onDone.run();
            }
        });
    }

    /** Below Android 12 exact alarms need no special access. */
    public static boolean canScheduleExactAlarms(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = context.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    @WorkerThread
    private static void reschedule(Context context) {
        NotificationSettings settings = NotificationSettings.getInstance(context);
        AlarmManager am = context.getSystemService(AlarmManager.class);
        if (am == null) return;
        long now = System.currentTimeMillis();
        List<Session> sessions = loadSessions(context);

        for (int code : settings.getScheduledAlarmCodes()) {
            PendingIntent pi = PendingIntent.getBroadcast(context, code, reminderIntent(context),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        }

        List<Reminder> reminders = settings.isRemindersEnabled()
                ? ReminderPlanner.planReminders(sessions, settings.getSessions(),
                        settings.getLeadMinutes(), now)
                : Collections.emptyList();
        boolean exact = canScheduleExactAlarms(context);
        Set<Integer> codes = new HashSet<>();
        for (Reminder r : reminders) {
            setAlarm(context, am, r, exact);
            codes.add(r.session.requestCode());
        }
        settings.setScheduledAlarmCodes(codes);
        DebugLog.d(TAG, "set " + codes.size() + " reminders (exact=" + exact + ")");

        WorkManager wm = WorkManager.getInstance(context);
        if (settings.isResultsEnabled()) {
            for (ResultCheck check : ReminderPlanner.planResultChecks(
                    sessions, settings.getNotifiedKeys(), now)) {
                ResultsWorker.enqueue(wm, check.session, check.delayMillis);
            }
        } else {
            wm.cancelAllWorkByTag(ResultsWorker.TAG);
        }
    }

    private static void setAlarm(Context context, AlarmManager am, Reminder r, boolean exact) {
        Session s = r.session;
        Intent intent = reminderIntent(context)
                .putExtra(EXTRA_YEAR, s.year)
                .putExtra(EXTRA_ROUND, s.round)
                .putExtra(EXTRA_SESSION, s.name)
                .putExtra(EXTRA_RACE_NAME, s.raceName)
                .putExtra(EXTRA_START, s.startMillis);
        PendingIntent pi = PendingIntent.getBroadcast(context, s.requestCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (exact) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.triggerAtMillis, pi);
                return;
            } catch (SecurityException e) {
                // Exact alarm access revoked since the check: fall through to a window
            }
        }
        // Centred on the reminder time, so it's at most 2.5 min early or late
        am.setWindow(AlarmManager.RTC_WAKEUP, r.triggerAtMillis - WINDOW_MS / 2, WINDOW_MS, pi);
    }

    /** Alarms are matched on component + action + request code; extras don't matter. */
    private static Intent reminderIntent(Context context) {
        return new Intent(context, ReminderReceiver.class).setAction(ReminderReceiver.ACTION_REMINDER);
    }

    /** Sessions of last, this and next season: the planning windows can cross New Year. */
    @WorkerThread
    @NonNull
    public static List<Session> loadSessions(@NonNull Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        int year = SeasonHelper.getCurrentYear();
        List<Session> out = new ArrayList<>();
        for (int y = year - 1; y <= year + 1; y++) {
            for (CachedSchedule row : db.scheduleDao().getByYear(y)) {
                out.addAll(ReminderPlanner.parseRound(row.year, row.round, row.raceName,
                        row.sessionsJson));
            }
        }
        return out;
    }
}
