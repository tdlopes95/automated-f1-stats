package com.f1stats.widget;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the favourite driver widget shows, kept in SharedPreferences so the widget
 * renders instantly and offline. Rebuilt by {@link FavouriteWidgetUpdater}; a cache only.
 */
public class WidgetSnapshot {

    private static final String PREFS_NAME = "widget_snapshot";
    private static final String KEY_SNAPSHOT = "favourite_driver";
    private static final Gson GSON = new Gson();

    /** The favourite this snapshot was built for; null when there was none. */
    @Nullable public String driverId;
    @Nullable public String name;
    @Nullable public String code;
    @Nullable public String constructorId;
    @Nullable public String teamName;
    /** OpenF1 team colour, preferred by TeamColors. */
    @Nullable public String teamColour;
    @Nullable public String headshotUrl;

    /** False when the driver isn't in this season's standings (or they didn't load). */
    public boolean inStandings;
    public int position;
    public double points;
    /** Points behind the driver one place ahead; 0 for the leader. */
    public double gapToAhead;

    /** The latest Race result; lastRaceName null when there is none this season. */
    @Nullable public String lastRaceName;
    /** Classified position, 0 if none. */
    public int lastRacePosition;
    @Nullable public String lastRaceStatus;

    /** Upcoming sessions, soonest first, so the next one stays right between refreshes. */
    public List<Session> upcoming = new ArrayList<>();

    public long updatedAt;

    public static class Session {
        public String name;
        public String raceName;
        public long startMillis;

        public Session() {}

        public Session(String name, String raceName, long startMillis) {
            this.name = name;
            this.raceName = raceName;
            this.startMillis = startMillis;
        }
    }

    /** The first session starting after {@code now}, or null. */
    @Nullable
    public Session nextSession(long now) {
        if (upcoming == null) return null;
        for (Session s : upcoming) {
            if (s != null && s.startMillis > now) return s;
        }
        return null;
    }

    // ── Storage ───────────────────────────────────────────────────────────────

    @Nullable
    public static WidgetSnapshot load(@NonNull Context context) {
        return fromJson(prefs(context).getString(KEY_SNAPSHOT, null));
    }

    public void save(@NonNull Context context) {
        // commit(): the caller re-renders the widgets from it straight after, often in a worker
        prefs(context).edit().putString(KEY_SNAPSHOT, GSON.toJson(this)).commit();
    }

    @Nullable
    static WidgetSnapshot fromJson(@Nullable String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            WidgetSnapshot snapshot = GSON.fromJson(json, WidgetSnapshot.class);
            if (snapshot != null && snapshot.upcoming == null) snapshot.upcoming = new ArrayList<>();
            return snapshot;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
