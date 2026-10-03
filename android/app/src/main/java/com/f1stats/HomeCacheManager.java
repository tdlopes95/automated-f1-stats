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
    private static final String KEY_LEADER_INSIGHT = "leader_insight";
    private static final String KEY_P2_NAME   = "p2_name";
    private static final String KEY_P2_TEAM   = "p2_team";
    private static final String KEY_P2_POINTS = "p2_points";
    private static final String KEY_LEADER_CODE = "leader_code";
    private static final String KEY_P2_CODE     = "p2_code";
    private static final String KEY_LAST_CODE   = "last_code";
    private static final String KEY_HEADSHOTS   = "headshots";
    private static final String KEY_CIRCUIT_IMAGE = "next_race_circuit_image";

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

    public void saveLeaderInsight(String insight) {
        prefs.edit().putString(KEY_LEADER_INSIGHT, insight).apply();
    }

    public void saveP2(String name, String team, String points) {
        prefs.edit()
                .putString(KEY_P2_NAME, name)
                .putString(KEY_P2_TEAM, team)
                .putString(KEY_P2_POINTS, points)
                .apply();
    }

    public void saveLastWinner(String winner, String team, String raceName) {
        prefs.edit()
                .putString(KEY_LAST_WINNER, winner)
                .putString(KEY_LAST_TEAM, team)
                .putString(KEY_LAST_RACE_NAME, raceName)
                .apply();
    }

    /** Driver codes for the cached leader, P2 and last winner, so their headshots can bind. */
    public void saveCodes(String leaderCode, String p2Code, String lastWinnerCode) {
        SharedPreferences.Editor editor = prefs.edit();
        if (leaderCode != null) editor.putString(KEY_LEADER_CODE, leaderCode);
        if (p2Code != null) editor.putString(KEY_P2_CODE, p2Code);
        if (lastWinnerCode != null) editor.putString(KEY_LAST_CODE, lastWinnerCode);
        editor.apply();
    }

    /** Driver code to headshot URL, so the first Home frame has them. */
    public void saveHeadshots(Map<String, String> headshots) {
        prefs.edit().putString(KEY_HEADSHOTS, gson.toJson(headshots)).apply();
    }

    public void saveCircuitImage(String url) {
        prefs.edit().putString(KEY_CIRCUIT_IMAGE, url).apply();
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
    public String loadLeaderInsight() { return prefs.getString(KEY_LEADER_INSIGHT, null); }
    public String loadLastWinner()   { return prefs.getString(KEY_LAST_WINNER, null); }
    public String loadLastTeam()     { return prefs.getString(KEY_LAST_TEAM, null); }
    public String loadLastRaceName() { return prefs.getString(KEY_LAST_RACE_NAME, null); }
    public String loadP2Name()   { return prefs.getString(KEY_P2_NAME, null); }
    public String loadP2Team()   { return prefs.getString(KEY_P2_TEAM, null); }
    public String loadP2Points() { return prefs.getString(KEY_P2_POINTS, null); }

    public String loadLeaderCode()     { return prefs.getString(KEY_LEADER_CODE, null); }
    public String loadP2Code()         { return prefs.getString(KEY_P2_CODE, null); }
    public String loadLastWinnerCode() { return prefs.getString(KEY_LAST_CODE, null); }
    public String loadCircuitImage()   { return prefs.getString(KEY_CIRCUIT_IMAGE, null); }

    /** Null if none were saved. */
    public Map<String, String> loadHeadshots() {
        String json = prefs.getString(KEY_HEADSHOTS, null);
        if (json == null) return null;
        Type type = new TypeToken<Map<String, String>>(){}.getType();
        return gson.fromJson(json, type);
    }

    /** Whether any card has something to show before the network answers. */
    public boolean hasCache() {
        return prefs.contains(KEY_LEADER_NAME) || prefs.contains(KEY_NEXT_RACE)
                || prefs.contains(KEY_LAST_WINNER);
    }
}