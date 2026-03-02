package com.f1stats.api;

import com.f1stats.models.ConstructorStanding;
import com.f1stats.models.DriverStanding;
import com.f1stats.models.LiveSession;
import com.f1stats.models.PitStop;
import com.f1stats.models.RaceResult;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface F1ApiService {

    // ── Schedule ──────────────────────────────────────────────────────────────

    @GET("schedule")
    Call<List<Map<String, Object>>> getSchedule();

    @GET("schedule")
    Call<List<Map<String, Object>>> getScheduleByYear(@Query("year") int year);

    @GET("schedule/next")
    Call<Map<String, Object>> getNextRace();

    @GET("schedule/upcoming-sessions")
    Call<List<Map<String, Object>>> getUpcomingSessions(@Query("days") int days);

    // ── Live ──────────────────────────────────────────────────────────────────

    @GET("live")
    Call<LiveSession> getLiveSession();

    @GET("live/{session_key}")
    Call<LiveSession> getLiveSessionByKey(@Path("session_key") int sessionKey);

    // ── Results ───────────────────────────────────────────────────────────────

    @GET("results/latest")
    Call<Map<String, Object>> getLatestResults(
            @Query("session_type") String sessionType,
            @Query("year") int year
    );

    @GET("results/{year}/{round}")
    Call<Map<String, Object>> getResults(
            @Path("year") int year,
            @Path("round") int round,
            @Query("session_type") String sessionType
    );

    // ── Standings ─────────────────────────────────────────────────────────────

    @GET("standings/drivers")
    Call<Map<String, Object>> getDriverStandings(@Query("year") int year);

    @GET("standings/constructors")
    Call<Map<String, Object>> getConstructorStandings(@Query("year") int year);

    // ── Session Details ───────────────────────────────────────────────────────

    @GET("sessions/{session_key}/pit-stops")
    Call<List<PitStop>> getPitStops(@Path("session_key") int sessionKey);

    @GET("sessions/{session_key}/stints")
    Call<List<Map<String, Object>>> getStints(@Path("session_key") int sessionKey);

    @GET("sessions/{session_key}/laps")
    Call<List<Map<String, Object>>> getLaps(@Path("session_key") int sessionKey);

    @GET("sessions/{session_key}/drivers")
    Call<List<Map<String, Object>>> getDrivers(@Path("session_key") int sessionKey);

    @GET("sessions/{session_key}/race-control")
    Call<List<Map<String, Object>>> getRaceControl(@Path("session_key") int sessionKey);

    @GET("sessions/{session_key}/weather")
    Call<Map<String, Object>> getWeather(@Path("session_key") int sessionKey);

    @GET("sessions")
    Call<List<Map<String, Object>>> getSessions(
            @Query("year") int year,
            @Query("session_type") String sessionType
    );

    @GET("session-key/{year}/{round}")
    Call<Map<String, Object>> getSessionKey(
            @Path("year") int year,
            @Path("round") int round
    );
}