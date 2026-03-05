package com.f1stats;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

public class HomeCacheManager {

    private static final String PREFS_NAME     = "home_cache";
    private static final String KEY_NEXT_RACE  = "next_race";
    private static final String KEY_LEADER_NAME   = "leader_name";
    private static final String KEY_LEADER_TEAM   = "leader_team";
    private static final String KEY_LEADER_POINTS = "leader_points";
    private static final String KEY_LEADER_GAP    = "leader_gap";
    private static final String KEY_SEASON_STARTED = "season_started";
    private static final String KEY_LAST_WINNER   = "last_winner";
    private static final String KEY_LAST_TEAM     = "last_team";
    private static final String KEY_LAST_RACE_NAME = "last_race_name";

    private static HomeCacheManager instance;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    private HomeCacheManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized HomeCacheManager getInstance(Context context) {
        if (instance == null) instance = new HomeCacheManager(context);
        return instance;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    public void saveNextRace(Map<String, Object> race) {
        prefs.edit().putString(KEY_NEXT_RACE, gson.toJson(race)).apply();
    }

    public void saveLeader(String name, String team, String points, double gap, boolean seasonStarted) {
        prefs.edit()
                .putString(KEY_LEADER_NAME, name)
                .putString(KEY_LEADER_TEAM, team)
                .putString(KEY_LEADER_POINTS, points)
                .putFloat(KEY_LEADER_GAP, (float) gap)
                .putBoolean(KEY_SEASON_STARTED, seasonStarted)
                .apply();
    }

    public void saveLastWinner(String winner, String team, String raceName) {
        prefs.edit()
                .putString(KEY_LAST_WINNER, winner)
                .putString(KEY_LAST_TEAM, team)
                .putString(KEY_LAST_RACE_NAME, raceName)
                .apply();
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public Map<String, Object> loadNextRace() {
        String json = prefs.getString(KEY_NEXT_RACE, null);
        if (json == null) return null;
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        return gson.fromJson(json, type);
    }

    public String loadLeaderName()   { return prefs.getString(KEY_LEADER_NAME, null); }
    public String loadLeaderTeam()   { return prefs.getString(KEY_LEADER_TEAM, null); }
    public String loadLeaderPoints() { return prefs.getString(KEY_LEADER_POINTS, null); }
    public float  loadLeaderGap()    { return prefs.getFloat(KEY_LEADER_GAP, 0f); }
    public boolean loadSeasonStarted() { return prefs.getBoolean(KEY_SEASON_STARTED, true); }
    public String loadLastWinner()   { return prefs.getString(KEY_LAST_WINNER, null); }
    public String loadLastTeam()     { return prefs.getString(KEY_LAST_TEAM, null); }
    public String loadLastRaceName() { return prefs.getString(KEY_LAST_RACE_NAME, null); }

    public boolean hasCache() {
        return prefs.getString(KEY_LEADER_NAME, null) != null;
    }
}