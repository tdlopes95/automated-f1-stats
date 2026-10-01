package com.f1stats.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.f1stats.F1App;
import com.f1stats.api.F1ApiClient;
import com.f1stats.api.F1ApiService;
import com.f1stats.data.F1Repository;
import com.f1stats.util.HeadToHead;
import com.f1stats.db.CachedDriver;
import com.f1stats.models.ConstructorStanding;
import com.f1stats.models.DriverStanding;
import com.f1stats.models.LiveSession;
import com.f1stats.models.NewsResponse;
import com.f1stats.models.OnThisDayResponse;
import com.f1stats.models.PitStop;
import com.f1stats.models.QualifyingResult;
import com.f1stats.models.RaceResult;
import com.f1stats.models.WeatherForecast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class F1ViewModel extends ViewModel {

    private final F1Repository repo = F1Repository.getInstance(F1App.get());

    // Resolved per call so a base-URL change in Settings (F1ApiClient.reset) applies immediately
    private F1ApiService api() {
        return F1ApiClient.getInstance(F1App.get()).getService();
    }

    // ── Live Session ──────────────────────────────────────────────────────────
    private final MutableLiveData<LiveSession> liveSession = new MutableLiveData<>();
    private final MutableLiveData<Boolean> liveLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> liveError = new MutableLiveData<>();

    // ── Results ───────────────────────────────────────────────────────────────
    private final MutableLiveData<List<RaceResult>> raceResults = new MutableLiveData<>();
    private final MutableLiveData<List<QualifyingResult>> qualifyingResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> resultsLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> resultsError = new MutableLiveData<>();

    // ── Standings ─────────────────────────────────────────────────────────────
    private final MutableLiveData<List<DriverStanding>> driverStandings = new MutableLiveData<>();
    private final MutableLiveData<List<ConstructorStanding>> constructorStandings = new MutableLiveData<>();
    private final MutableLiveData<Boolean> standingsLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> standingsError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> seasonStarted = new MutableLiveData<>(true);

    // ── Pit Stops ─────────────────────────────────────────────────────────────
    private final MutableLiveData<List<PitStop>> pitStops = new MutableLiveData<>();
    private final MutableLiveData<Boolean> pitStopsLoading = new MutableLiveData<>(false);

    // ── Weather ───────────────────────────────────────────────────────────────
    private final MutableLiveData<Map<String, Object>> weatherData = new MutableLiveData<>();

    // ── Schedule ──────────────────────────────────────────────────────────────
    private final MutableLiveData<List<Map<String, Object>>> schedule = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Object>> nextRace = new MutableLiveData<>();
    private final MutableLiveData<Boolean> scheduleLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> scheduleError = new MutableLiveData<>();

    // ── Meetings ──────────────────────────────────────────────────────────────
    private final MutableLiveData<List<Map<String, Object>>> meetings = new MutableLiveData<>();

    // ── Season Stats ──────────────────────────────────────────────────────────
    private final MutableLiveData<Map<String, Integer>> dnfMap = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Integer>> podiumMap = new MutableLiveData<>();

    // ── Starting Grid ─────────────────────────────────────────────────────────
    private final MutableLiveData<List<Map<String, Object>>> startingGrid = new MutableLiveData<>();
    private final MutableLiveData<Boolean> startingGridLoading = new MutableLiveData<>(false);

    // ── Tyre Strategy ─────────────────────────────────────────────────────────
    private final MutableLiveData<List<Map<String, Object>>> stints = new MutableLiveData<>();
    private final MutableLiveData<Boolean> stintsLoading = new MutableLiveData<>(false);

    // ── Driver Headshots ──────────────────────────────────────────────────────
    private final MutableLiveData<Map<String, String>> driverHeadshotMap = new MutableLiveData<>();

    // ── Home card errors (null = last fetch didn't fail) ──────────────────────
    // One per Home data source, so a failing card never affects the others
    private final MutableLiveData<String> nextRaceError = new MutableLiveData<>(null);
    private final MutableLiveData<String> homeStandingsError = new MutableLiveData<>(null);
    private final MutableLiveData<String> latestResultsError = new MutableLiveData<>(null);
    private final MutableLiveData<String> homeConstructorStandingsError = new MutableLiveData<>(null);

    public LiveData<String> getNextRaceError() { return nextRaceError; }
    public LiveData<String> getHomeStandingsError() { return homeStandingsError; }
    public LiveData<String> getLatestResultsError() { return latestResultsError; }
    public LiveData<String> getHomeConstructorStandingsError() { return homeConstructorStandingsError; }

    // ── Home data: current season, shared by every Home card ──────────────────
    // Separate from the Standings tab's LiveData, which can hold another season
    private final MutableLiveData<List<DriverStanding>> homeDriverStandings = new MutableLiveData<>();
    private final MutableLiveData<List<ConstructorStanding>> homeConstructorStandings = new MutableLiveData<>();
    private final MutableLiveData<HeadToHead.Season> homeSeasonResults = new MutableLiveData<>();
    private boolean homeSeasonResultsInFlight;

    public LiveData<List<DriverStanding>> getHomeDriverStandings() { return homeDriverStandings; }
    public LiveData<List<ConstructorStanding>> getHomeConstructorStandings() { return homeConstructorStandings; }
    public LiveData<HeadToHead.Season> getHomeSeasonResults() { return homeSeasonResults; }

    // Latest race results for Home's last winner. Separate from raceResults, which the Results
    // and Weekend tabs fill with whatever round they show.
    private final MutableLiveData<List<RaceResult>> homeLatestResults = new MutableLiveData<>();
    private final MutableLiveData<String> homeLastRaceName = new MutableLiveData<>();

    public LiveData<List<RaceResult>> getHomeLatestResults() { return homeLatestResults; }
    public LiveData<String> getHomeLastRaceName() { return homeLastRaceName; }

    // ── Weekend forecast, news, history (Home cards; news also NewsActivity) ──
    private final MutableLiveData<WeatherForecast> weekendForecast = new MutableLiveData<>();
    private final MutableLiveData<String> weekendForecastError = new MutableLiveData<>(null);
    private final MutableLiveData<NewsResponse> news = new MutableLiveData<>();
    private final MutableLiveData<String> newsError = new MutableLiveData<>(null);
    private final MutableLiveData<OnThisDayResponse> onThisDay = new MutableLiveData<>();
    private final MutableLiveData<String> onThisDayError = new MutableLiveData<>(null);

    public LiveData<WeatherForecast> getWeekendForecast() { return weekendForecast; }
    public LiveData<String> getWeekendForecastError() { return weekendForecastError; }
    public LiveData<NewsResponse> getNews() { return news; }
    public LiveData<String> getNewsError() { return newsError; }
    public LiveData<OnThisDayResponse> getOnThisDay() { return onThisDay; }
    public LiveData<String> getOnThisDayError() { return onThisDayError; }
    public LiveData<Boolean> getSeasonStarted() { return seasonStarted; }
    public LiveData<Map<String, String>> getDriverHeadshotMap() { return driverHeadshotMap; }


    // ── Live Session (no caching — always live) ───────────────────────────────

    public void fetchLiveSession() {
        liveLoading.setValue(true);
        api().getLiveSession().enqueue(new Callback<LiveSession>() {
            @Override
            public void onResponse(Call<LiveSession> call, Response<LiveSession> response) {
                liveLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    liveSession.setValue(response.body());
                } else {
                    liveError.setValue(httpError(response.code()));
                }
            }
            @Override
            public void onFailure(Call<LiveSession> call, Throwable t) {
                liveLoading.setValue(false);
                liveError.setValue(networkError(t));
            }
        });
    }


    // ── Results ───────────────────────────────────────────────────────────────

    /** Home's last winner: the latest race of the season, into Home's own LiveData. */
    public void fetchHomeLatestResults(int year) {
        latestResultsError.setValue(null);
        api().getLatestResults("Race", year).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call,
                                   Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> body = response.body();
                    Object rn = body.get("race_name");
                    if (rn != null) homeLastRaceName.setValue(rn.toString());
                    homeLatestResults.setValue(parseRaceResults(body));
                } else {
                    latestResultsError.setValue(httpError(response.code()));
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                latestResultsError.setValue(networkError(t));
            }
        });
    }

    public void fetchResults(int year, int round, String sessionType) {
        resultsLoading.setValue(true);
        repo.getResults(year, round, sessionType, new F1Repository.RepositoryCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                resultsLoading.setValue(false);
                raceResults.setValue(parseRaceResults(data));
            }
            @Override
            public void onError(String error) {
                resultsLoading.setValue(false);
                resultsError.setValue(error);
            }
        });
    }

    public void fetchQualifyingResults(int year, int round) {
        resultsLoading.setValue(true);
        qualifyingResults.setValue(null);
        repo.getResults(year, round, "Qualifying", new F1Repository.RepositoryCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                resultsLoading.setValue(false);
                try {
                    Object results = data.get("results");
                    if (results instanceof List) {
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        String json = gson.toJson(results);
                        java.lang.reflect.Type type =
                                new com.google.gson.reflect.TypeToken<List<QualifyingResult>>(){}.getType();
                        qualifyingResults.setValue(gson.fromJson(json, type));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onError(String error) {
                resultsLoading.setValue(false);
            }
        });
    }


    // ── Standings ─────────────────────────────────────────────────────────────

    public void fetchDriverStandings(int year) {
        standingsLoading.setValue(true);
        driverStandings.setValue(null);
        repo.getDriverStandings(year, new F1Repository.RepositoryCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                standingsLoading.setValue(false);
                Object started = data.get("season_started");
                if (started instanceof Boolean) {
                    seasonStarted.setValue((Boolean) started);
                }
                driverStandings.setValue(parseDriverStandings(data));
            }
            @Override
            public void onError(String error) {
                standingsLoading.setValue(false);
                standingsError.setValue(error);
            }
        });
    }

    public void fetchConstructorStandings(int year) {
        standingsLoading.setValue(true);
        constructorStandings.setValue(null);
        repo.getConstructorStandings(year, new F1Repository.RepositoryCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                standingsLoading.setValue(false);
                constructorStandings.setValue(parseConstructorStandings(data));
            }
            @Override
            public void onError(String error) {
                standingsLoading.setValue(false);
                standingsError.setValue(error);
            }
        });
    }

    // ── Home standings and season results ─────────────────────────────────────

    public void fetchHomeDriverStandings(int year) {
        homeStandingsError.setValue(null);
        repo.getDriverStandings(year, new F1Repository.RepositoryCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                Object started = data.get("season_started");
                if (started instanceof Boolean) {
                    seasonStarted.setValue((Boolean) started);
                }
                homeDriverStandings.setValue(parseDriverStandings(data));
            }
            @Override
            public void onError(String error) {
                homeStandingsError.setValue(error);
            }
        });
    }

    public void fetchHomeConstructorStandings(int year) {
        homeConstructorStandingsError.setValue(null);
        repo.getConstructorStandings(year, new F1Repository.RepositoryCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                homeConstructorStandings.setValue(parseConstructorStandings(data));
            }
            @Override
            public void onError(String error) {
                homeConstructorStandingsError.setValue(error);
            }
        });
    }

    /**
     * Fetches any missing rounds of the season, then publishes them parsed. A call while one
     * is in flight is ignored. Never fails: what's cached is what's published.
     */
    public void fetchHomeSeasonResults(int year) {
        if (homeSeasonResultsInFlight) return;
        homeSeasonResultsInFlight = true;
        repo.getSeasonResults(year, new F1Repository.RepositoryCallback<HeadToHead.Season>() {
            @Override
            public void onSuccess(HeadToHead.Season season) {
                homeSeasonResultsInFlight = false;
                homeSeasonResults.setValue(season);
            }
            @Override
            public void onError(String error) {
                homeSeasonResultsInFlight = false;
                homeSeasonResults.setValue(new HeadToHead.Season(year));
            }
        });
    }

    public void clearStandings() {
        driverStandings.setValue(null);
        constructorStandings.setValue(null);
    }


    // ── Pit Stops ─────────────────────────────────────────────────────────────

    public void fetchPitStops(int sessionKey) {
        pitStopsLoading.setValue(true);
        api().getPitStops(sessionKey).enqueue(new Callback<List<PitStop>>() {
            @Override
            public void onResponse(Call<List<PitStop>> call, Response<List<PitStop>> response) {
                pitStopsLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    List<PitStop> stops = response.body();
                    Collections.sort(stops, (a, b) ->
                            Double.compare(a.getRankingTime(), b.getRankingTime()));
                    pitStops.setValue(stops);
                }
            }
            @Override
            public void onFailure(Call<List<PitStop>> call, Throwable t) {
                pitStopsLoading.setValue(false);
            }
        });
    }

    public void fetchPitStopsForRace(int year, int round) {
        pitStopsLoading.setValue(true);
        pitStops.setValue(null);
        repo.getSessionKey(year, round, new F1Repository.RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer sessionKey) {
                fetchPitStops(sessionKey);
            }
            @Override
            public void onError(String error) {
                pitStopsLoading.setValue(false);
            }
        });
    }


    public LiveData<Map<String, Object>> getWeatherData() { return weatherData; }

    public void fetchWeatherForRace(int year, int round) {
        weatherData.setValue(null);
        repo.getSessionKey(year, round, new F1Repository.RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer sessionKey) {
                api().getWeather(sessionKey).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                                           Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            weatherData.setValue(response.body());
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
            }
            @Override
            public void onError(String error) {}
        });
    }


    // ── Schedule ──────────────────────────────────────────────────────────────

    public void fetchSchedule(int year) {
        scheduleLoading.setValue(true);
        repo.getSchedule(year, new F1Repository.RepositoryCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                scheduleLoading.setValue(false);
                schedule.setValue(data);
            }
            @Override
            public void onError(String error) {
                scheduleLoading.setValue(false);
                scheduleError.setValue(error);
            }
        });
    }

    public void fetchNextRace() {
        nextRaceError.setValue(null);
        api().getNextRace().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call,
                                   Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    nextRace.setValue(response.body());
                } else {
                    nextRaceError.setValue(httpError(response.code()));
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                nextRaceError.setValue(networkError(t));
            }
        });
    }


    // ── Weekend forecast, news, history ───────────────────────────────────────

    public void fetchWeekendForecast(int year, int round, boolean forceRefresh) {
        weekendForecastError.setValue(null);
        repo.getWeatherForecast(year, round, forceRefresh, new F1Repository.RepositoryCallback<WeatherForecast>() {
            @Override
            public void onSuccess(WeatherForecast data) {
                weekendForecast.setValue(data);
            }
            @Override
            public void onError(String error) {
                weekendForecastError.setValue(error);
            }
        });
    }

    public void fetchNews(int limit, boolean forceRefresh) {
        newsError.setValue(null);
        repo.getNews(limit, forceRefresh, new F1Repository.RepositoryCallback<NewsResponse>() {
            @Override
            public void onSuccess(NewsResponse data) {
                news.setValue(data);
            }
            @Override
            public void onError(String error) {
                newsError.setValue(error);
            }
        });
    }

    /** @param dateIso the local date, YYYY-MM-DD */
    public void fetchOnThisDay(String dateIso) {
        onThisDayError.setValue(null);
        repo.getOnThisDay(dateIso, new F1Repository.RepositoryCallback<OnThisDayResponse>() {
            @Override
            public void onSuccess(OnThisDayResponse data) {
                onThisDay.setValue(data);
            }
            @Override
            public void onError(String error) {
                onThisDayError.setValue(error);
            }
        });
    }


    // ── Meetings ──────────────────────────────────────────────────────────────

    public void fetchMeetings(int year) {
        repo.getMeetings(year, new F1Repository.RepositoryCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> data) {
                meetings.setValue(data);
            }
            @Override
            public void onError(String error) {}
        });
    }

    // ── Driver Headshots ──────────────────────────────────────────────────────

    public void prefetchDrivers(int year) {
        repo.fetchDrivers(year, new F1Repository.RepositoryCallback<List<CachedDriver>>() {
            @Override
            public void onSuccess(List<CachedDriver> drivers) {
                Map<String, String> map = new java.util.HashMap<>();
                for (CachedDriver d : drivers) {
                    if (d.code != null && d.headshotUrl != null && !d.headshotUrl.isEmpty()) {
                        map.put(d.code, d.headshotUrl);
                    }
                }
                driverHeadshotMap.setValue(map);
            }
            @Override
            public void onError(String error) {}
        });
    }

    // ── Starting Grid ─────────────────────────────────────────────────────────

    public void fetchStartingGridForRace(int year, int round) {
        startingGridLoading.setValue(true);
        startingGrid.setValue(null);
        repo.getStartingGridFromResults(year, round,
                new F1Repository.RepositoryCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> grid) {
                startingGridLoading.setValue(false);
                startingGrid.setValue(grid);
            }
            @Override
            public void onError(String error) {
                android.util.Log.e("F1ViewModel", "Starting grid error: " + error);
                startingGridLoading.setValue(false);
                startingGrid.setValue(new ArrayList<>());
            }
        });
    }

    // ── Tyre Strategy ─────────────────────────────────────────────────────────

    public void fetchStintsForRace(int year, int round) {
        stintsLoading.setValue(true);
        stints.setValue(null);
        if (year < 2023) {
            stintsLoading.setValue(false);
            stints.setValue(new ArrayList<>());
            return;
        }
        repo.getSessionKey(year, round, new F1Repository.RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer sessionKey) {
                api().getStints(sessionKey).enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        stintsLoading.setValue(false);
                        if (response.isSuccessful() && response.body() != null) {
                            stints.setValue(response.body());
                        } else {
                            stints.setValue(new ArrayList<>());
                        }
                    }
                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        stintsLoading.setValue(false);
                        stints.setValue(new ArrayList<>());
                    }
                });
            }
            @Override
            public void onError(String error) {
                stintsLoading.setValue(false);
                stints.setValue(new ArrayList<>());
            }
        });
    }

    // ── Season Stats ──────────────────────────────────────────────────────────

    public void fetchSeasonStats(int year) {
        repo.fetchSeasonStats(year, (dnfs, podiums) -> {
            dnfMap.setValue(dnfs);
            podiumMap.setValue(podiums);
        });
    }


    // ── Exposed LiveData ──────────────────────────────────────────────────────

    public LiveData<LiveSession> getLiveSession() { return liveSession; }
    public LiveData<Boolean> getLiveLoading() { return liveLoading; }
    public LiveData<String> getLiveError() { return liveError; }

    public LiveData<List<RaceResult>> getRaceResults() { return raceResults; }
    public LiveData<List<QualifyingResult>> getQualifyingResults() { return qualifyingResults; }
    public LiveData<Boolean> getResultsLoading() { return resultsLoading; }
    public LiveData<String> getResultsError() { return resultsError; }

    public LiveData<List<DriverStanding>> getDriverStandings() { return driverStandings; }
    public LiveData<List<ConstructorStanding>> getConstructorStandings() { return constructorStandings; }
    public LiveData<Boolean> getStandingsLoading() { return standingsLoading; }
    public LiveData<String> getStandingsError() { return standingsError; }

    public LiveData<List<PitStop>> getPitStops() { return pitStops; }
    public LiveData<Boolean> getPitStopsLoading() { return pitStopsLoading; }

    public LiveData<List<Map<String, Object>>> getSchedule() { return schedule; }
    public LiveData<Map<String, Object>> getNextRace() { return nextRace; }
    public LiveData<Boolean> getScheduleLoading() { return scheduleLoading; }
    public LiveData<String> getScheduleError() { return scheduleError; }

    public LiveData<Map<String, Integer>> getDnfMap() { return dnfMap; }
    public LiveData<Map<String, Integer>> getPodiumMap() { return podiumMap; }

    public LiveData<List<Map<String, Object>>> getMeetings() { return meetings; }

    public LiveData<List<Map<String, Object>>> getStartingGrid() { return startingGrid; }
    public LiveData<Boolean> getStartingGridLoading() { return startingGridLoading; }

    public LiveData<List<Map<String, Object>>> getStints() { return stints; }
    public LiveData<Boolean> getStintsLoading() { return stintsLoading; }


    // ── Private Parsers ───────────────────────────────────────────────────────

    private List<RaceResult> parseRaceResults(Map<String, Object> body) {
        try {
            Object results = body.get("results");
            if (results instanceof List) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                String json = gson.toJson(results);
                java.lang.reflect.Type type =
                        new com.google.gson.reflect.TypeToken<List<RaceResult>>(){}.getType();
                return gson.fromJson(json, type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private List<DriverStanding> parseDriverStandings(Map<String, Object> body) {
        try {
            Object standings = body.get("standings");
            if (standings instanceof List) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                String json = gson.toJson(standings);
                java.lang.reflect.Type type =
                        new com.google.gson.reflect.TypeToken<List<DriverStanding>>(){}.getType();
                return gson.fromJson(json, type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private List<ConstructorStanding> parseConstructorStandings(Map<String, Object> body) {
        try {
            Object standings = body.get("standings");
            if (standings instanceof List) {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                String json = gson.toJson(standings);
                java.lang.reflect.Type type =
                        new com.google.gson.reflect.TypeToken<List<ConstructorStanding>>(){}.getType();
                return gson.fromJson(json, type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private static String networkError(Throwable t) {
        if (t instanceof java.net.SocketTimeoutException) {
            return "Request timed out. Pull down to retry.";
        }
        if (t instanceof java.net.UnknownHostException || t instanceof java.net.ConnectException) {
            return "Couldn't connect to server. Pull down to retry.";
        }
        return "Something went wrong. Pull down to retry.";
    }

    private static String httpError(int code) {
        if (code >= 500) {
            return "Server is having issues. Try again in a moment.";
        }
        return "Something went wrong. Pull down to retry.";
    }
}
