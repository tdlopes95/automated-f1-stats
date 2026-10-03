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
    /** Bumped when fields are added, so older snapshots count as stale and get rebuilt. */
    static final int CURRENT_VERSION = 2;

    /** Set on save; 0 in snapshots from before versioning. */
    public int version;

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
    /** Points behind the championship leader; 0 for the leader. */
    public double gapToLeader;

    /** Race wins and podiums this season (from the season results). */
    public int wins;
    public int podiums;
    /** Up to the last five Race results, oldest first; empty when there are none yet. */
    public List<Result> lastResults = new ArrayList<>();

    /** Code of the teammate the race H2H is against; null when there is none. */
    @Nullable public String teammateCode;
    /** Race H2H tally: rounds this driver finished ahead, and behind. */
    public int h2hWins;
    public int h2hLosses;

    /** Upcoming sessions, soonest first, so the next one stays right between refreshes. */
    public List<Session> upcoming = new ArrayList<>();

    public long updatedAt;

    public static class Result {
        @Nullable public String raceName;
        /** Classified position, 0 if none. */
        public int position;
        @Nullable public String status;

        public Result() {}

        public Result(@Nullable String raceName, int position, @Nullable String status) {
            this.raceName = raceName;
            this.position = position;
            this.status = status;
        }
    }

    public static class Session {
        public String name;
        public String raceName;
        public long startMillis;
        /** Start of the race weekend's first session; 0 in snapshots from before it was kept. */
        public long weekendStartMillis;

        public Session() {}

        public Session(String name, String raceName, long startMillis) {
            this(name, raceName, startMillis, startMillis);
        }

        public Session(String name, String raceName, long startMillis, long weekendStartMillis) {
            this.name = name;
            this.raceName = raceName;
            this.startMillis = startMillis;
            this.weekendStartMillis = weekendStartMillis;
        }

        /** The weekend's first session, or this one when that wasn't stored. */
        public long weekendStart() {
            return weekendStartMillis > 0 ? weekendStartMillis : startMillis;
        }
    }

    /** The latest Race result, or null. */
    @Nullable
    public Result lastResult() {
        return lastResults == null || lastResults.isEmpty() ? null : lastResults.get(lastResults.size() - 1);
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
        version = CURRENT_VERSION;
        // commit(): the caller re-renders the widgets from it straight after, often in a worker
        prefs(context).edit().putString(KEY_SNAPSHOT, GSON.toJson(this)).commit();
    }

    @Nullable
    static WidgetSnapshot fromJson(@Nullable String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            WidgetSnapshot snapshot = GSON.fromJson(json, WidgetSnapshot.class);
            if (snapshot == null) return null;
            if (snapshot.upcoming == null) snapshot.upcoming = new ArrayList<>();
            if (snapshot.lastResults == null) snapshot.lastResults = new ArrayList<>();
            return snapshot;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
