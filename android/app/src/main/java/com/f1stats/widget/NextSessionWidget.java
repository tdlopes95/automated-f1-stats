package com.f1stats.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.DateHelper;
import com.f1stats.MainActivity;
import com.f1stats.R;
import com.f1stats.SeasonHelper;
import com.f1stats.db.AppDatabase;
import com.f1stats.db.CachedSchedule;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NextSessionWidget extends AppWidgetProvider {

    static final String ACTION_TICK = "com.f1stats.widget.TICK";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        // Called from within onReceive, so goAsync() is valid here
        updateAllWidgets(context, manager, goAsync());
    }

    @Override
    public void onEnabled(Context context) {
        scheduleMinuteTick(context);
    }

    @Override
    public void onDisabled(Context context) {
        cancelMinuteTick(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_TICK.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            updateAllWidgets(context, manager, goAsync());
            scheduleMinuteTick(context);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /** Room lookup runs off the main thread; the receiver stays alive until {@code result} finishes. */
    private static void updateAllWidgets(Context context, AppWidgetManager manager,
                                         PendingResult result) {
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, NextSessionWidget.class));
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                NextSessionInfo info = findNextSession(appContext);
                for (int id : ids) {
                    applyViews(appContext, manager, id, info);
                }
            } finally {
                result.finish();
            }
        }).start();
    }

    private static void applyViews(Context context, AppWidgetManager manager,
                                   int widgetId, NextSessionInfo info) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget_next_session);

        if (info != null) {
            views.setTextViewText(R.id.widget_race_name, info.raceName);
            views.setTextViewText(R.id.widget_session_type, info.sessionName);
            views.setTextViewText(R.id.widget_countdown, formatCountdown(info.millisUntil));
        } else {
            views.setTextViewText(R.id.widget_race_name, "No upcoming sessions");
            views.setTextViewText(R.id.widget_session_type, "");
            views.setTextViewText(R.id.widget_countdown, "");
        }

        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(
                context, 0, launch, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pending);

        manager.updateAppWidget(widgetId, views);
    }

    // ── DB lookup ─────────────────────────────────────────────────────────────

    private static final String TAG = "NextSessionWidget";
    private static final Gson GSON = new Gson();

    /** Null (the widget's empty state) if there's no next session or the cache can't be read. */
    @Nullable
    private static NextSessionInfo findNextSession(Context context) {
        try {
            return lookUpNextSession(context);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not read the cached schedule", e);
            return null;
        }
    }

    /**
     * The sessions of a cached schedule row. {@code sessionsJson} holds the whole race object,
     * as F1Repository.saveSchedule stores it; its "sessions" array is what we want.
     *
     * @throws com.google.gson.JsonParseException if the row isn't a JSON object
     */
    @NonNull
    static List<Map<String, Object>> parseSessions(@Nullable String sessionsJson) {
        if (sessionsJson == null) return new ArrayList<>();
        StoredRace race = GSON.fromJson(sessionsJson, StoredRace.class);
        return race != null && race.sessions != null ? race.sessions : new ArrayList<>();
    }

    /** The part of the stored race object the widget reads. */
    private static class StoredRace {
        List<Map<String, Object>> sessions;
    }

    private static NextSessionInfo lookUpNextSession(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        long now = System.currentTimeMillis();
        int year = SeasonHelper.getCurrentYear();

        List<CachedSchedule> rounds = db.scheduleDao().getByYear(year);

        for (CachedSchedule round : rounds) {
            List<Map<String, Object>> sessions = parseSessions(round.sessionsJson);
            for (Map<String, Object> session : sessions) {
                Object name = session.get("name");
                Object datetime = session.get("datetime");
                if (name == null || datetime == null) continue;

                long millis = DateHelper.toMillis(datetime.toString());
                if (millis > now) {
                    NextSessionInfo info = new NextSessionInfo();
                    info.raceName = round.raceName != null ? round.raceName : "";
                    info.sessionName = name.toString();
                    info.millisUntil = millis - now;
                    return info;
                }
            }
        }

        // If current year is exhausted, don't try to look up next year (no data yet)
        return null;
    }

    // ── Countdown formatting ──────────────────────────────────────────────────

    private static String formatCountdown(long millis) {
        if (millis <= 0) return "Starting soon";
        long totalSeconds = millis / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    // ── AlarmManager ─────────────────────────────────────────────────────────

    private static void scheduleMinuteTick(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pi = tickIntent(context);
        long nextMinute = SystemClock.elapsedRealtime() + 60_000L;
        alarmManager.set(AlarmManager.ELAPSED_REALTIME, nextMinute, pi);
    }

    private static void cancelMinuteTick(Context context) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        alarmManager.cancel(tickIntent(context));
    }

    private static PendingIntent tickIntent(Context context) {
        // Explicit: the TICK action isn't in the manifest filter, so only a targeted intent arrives
        Intent intent = new Intent(ACTION_TICK);
        intent.setClass(context, NextSessionWidget.class);
        return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ── Data class ────────────────────────────────────────────────────────────

    private static class NextSessionInfo {
        String raceName;
        String sessionName;
        long millisUntil;
    }
}
