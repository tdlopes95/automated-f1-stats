package com.f1stats.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.RemoteViews;

import com.f1stats.DateHelper;
import com.f1stats.MainActivity;
import com.f1stats.R;
import com.f1stats.SeasonHelper;
import com.f1stats.db.AppDatabase;
import com.f1stats.db.CachedSchedule;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class NextSessionWidget extends AppWidgetProvider {

    static final String ACTION_TICK = "com.f1stats.widget.TICK";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        updateAllWidgets(context, manager);
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
            updateAllWidgets(context, manager);
            scheduleMinuteTick(context);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static void updateAllWidgets(Context context, AppWidgetManager manager) {
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, NextSessionWidget.class));
        new Thread(() -> {
            NextSessionInfo info = findNextSession(context);
            for (int id : ids) {
                applyViews(context, manager, id, info);
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

    private static final Gson GSON = new Gson();
    private static final Type SESSION_LIST_TYPE =
            new TypeToken<List<Map<String, Object>>>() {}.getType();

    private static NextSessionInfo findNextSession(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        long now = System.currentTimeMillis();
        int year = SeasonHelper.getCurrentYear();

        List<CachedSchedule> rounds = db.scheduleDao().getByYear(year);

        for (CachedSchedule round : rounds) {
            if (round.sessionsJson == null) continue;
            List<Map<String, Object>> sessions =
                    GSON.fromJson(round.sessionsJson, SESSION_LIST_TYPE);
            if (sessions == null) continue;

            for (Map<String, Object> session : sessions) {
                String name = (String) session.get("name");
                String datetime = (String) session.get("datetime");
                if (name == null || datetime == null) continue;

                long millis = DateHelper.toMillis(datetime);
                if (millis > now) {
                    NextSessionInfo info = new NextSessionInfo();
                    info.raceName = round.raceName != null ? round.raceName : "";
                    info.sessionName = name;
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
        Intent intent = new Intent(context, NextSessionWidget.class);
        intent.setAction(ACTION_TICK);
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
