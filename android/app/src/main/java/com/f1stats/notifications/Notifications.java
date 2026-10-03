package com.f1stats.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.f1stats.DateHelper;
import com.f1stats.MainActivity;
import com.f1stats.R;
import com.f1stats.RoundDetailActivity;
import com.f1stats.util.DebugLog;

import java.util.List;

/** Channels and the two notification types: session reminders and favourite results. */
public final class Notifications {

    private Notifications() {}

    private static final String TAG = "Notifications";

    public static final String CHANNEL_REMINDERS = "session_reminders";
    public static final String CHANNEL_RESULTS = "race_results";

    /** Idempotent; called on every app start. */
    public static void createChannels(@NonNull Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_REMINDERS,
                context.getString(R.string.notif_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_RESULTS,
                context.getString(R.string.notif_channel_results),
                NotificationManager.IMPORTANCE_DEFAULT));
    }

    /** POST_NOTIFICATIONS is a runtime permission from Android 13. */
    public static boolean needsPermission(@NonNull Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED;
    }

    public static boolean canPost(@NonNull Context context) {
        return !needsPermission(context)
                && NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /** "{session} in {n} min" / "{race name} · {local start time}"; tap opens the Weekend tab. */
    public static void showReminder(@NonNull Context context, int year, int round,
                                    @NonNull String sessionName, @NonNull String raceName,
                                    long startMillis) {
        long minutes = Math.max(1, Math.round((startMillis - System.currentTimeMillis()) / 60_000.0));
        String title = context.getString(R.string.notif_reminder_title, sessionName, minutes);
        String time = DateHelper.formatLocalTime(startMillis, DateFormat.is24HourFormat(context));
        String text = context.getString(R.string.notif_reminder_text, raceName, time);

        int id = ReminderPlanner.requestCode(year, round, sessionName);
        Intent open = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_WEEKEND)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.f1_red))
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(startMillis)
                .setContentIntent(tap)
                .setAutoCancel(true);
        post(context, id, builder);
    }

    /** One notification with a line per favourite; tap opens the round's detail. */
    public static void showResults(@NonNull Context context, @NonNull ReminderPlanner.Session session,
                                   @NonNull List<String> lines) {
        boolean sprint = session.kind == ReminderPlanner.SessionKind.SPRINT;
        String title = context.getString(sprint
                ? R.string.notif_results_title_sprint : R.string.notif_results_title_race);
        String body = String.join("\n", lines);

        Intent detail = new Intent(context, RoundDetailActivity.class)
                .putExtra(RoundDetailActivity.EXTRA_YEAR, session.year)
                .putExtra(RoundDetailActivity.EXTRA_ROUND, session.round)
                .putExtra(RoundDetailActivity.EXTRA_RACE_NAME, session.raceName)
                .putExtra(RoundDetailActivity.EXTRA_CIRCUIT, session.circuit)
                .putExtra(RoundDetailActivity.EXTRA_CIRCUIT_ID, session.circuitId)
                .putExtra(RoundDetailActivity.EXTRA_HAS_SPRINT, session.hasSprint);
        // Negative ids keep result notifications apart from reminders for the same session
        int id = -session.requestCode();
        PendingIntent tap = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(detail)
                .getPendingIntent(id, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_RESULTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.f1_red))
                .setContentTitle(title)
                .setContentText(lines.get(0))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setContentIntent(tap)
                .setAutoCancel(true);
        post(context, id, builder);
    }

    private static void post(Context context, int id, NotificationCompat.Builder builder) {
        if (!canPost(context)) {
            DebugLog.d(TAG, "notifications not allowed, dropping " + id);
            return;
        }
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException e) {
            // Permission revoked between the check and the post
            DebugLog.d(TAG, "notify failed: " + e.getMessage());
        }
    }
}
