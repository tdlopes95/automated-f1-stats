package com.f1stats.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Shows a session reminder when its alarm fires, and reschedules after the system broadcasts
 * that clear alarms or shift local time (boot, clock/time zone change, app update, exact alarm
 * access granted).
 */
public class ReminderReceiver extends BroadcastReceiver {

    static final String ACTION_REMINDER = "com.f1stats.notifications.REMINDER";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case ACTION_REMINDER:
                showReminder(context, intent);
                break;
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED":
                PendingResult result = goAsync();
                ReminderScheduler.rescheduleAsync(context, result::finish);
                break;
            default:
                break;
        }
    }

    private static void showReminder(Context context, Intent intent) {
        if (!NotificationSettings.getInstance(context).isRemindersEnabled()) return;
        String session = intent.getStringExtra(ReminderScheduler.EXTRA_SESSION);
        String raceName = intent.getStringExtra(ReminderScheduler.EXTRA_RACE_NAME);
        long start = intent.getLongExtra(ReminderScheduler.EXTRA_START, 0);
        if (session == null || start <= 0) return;
        // An alarm delivered long after the session started (e.g. device asleep) is stale
        if (System.currentTimeMillis() > start) return;
        Notifications.showReminder(context,
                intent.getIntExtra(ReminderScheduler.EXTRA_YEAR, 0),
                intent.getIntExtra(ReminderScheduler.EXTRA_ROUND, 0),
                session, raceName != null ? raceName : "", start);
    }
}
