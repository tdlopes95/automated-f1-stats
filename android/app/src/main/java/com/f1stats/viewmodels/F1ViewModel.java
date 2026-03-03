package com.f1stats.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.f1stats.F1App;
import com.f1stats.api.F1ApiClient;
import com.f1stats.api.F1ApiService;
import com.f1stats.models.ConstructorStanding;
import com.f1stats.models.DriverStanding;
import com.f1stats.models.LiveSession;
import com.f1stats.models.PitStop;
import com.f1stats.models.QualifyingResult;
import com.f1stats.models.RaceResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class F1ViewModel extends ViewModel {

    private final F1ApiService api = F1ApiClient.getInstance(F1App.get()).getService();

    // ── Live Session ──────────────────────────────────────────────────────────
    private final MutableLiveData<LiveSession> liveSession = new MutableLiveData<>();
    private final MutableLiveData<Boolean> liveLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> liveError = new MutableLiveData<>();
    private final MutableLiveData<String> lastRaceName = new MutableLiveData<>();
    public LiveData<String> getLastRaceName() { return lastRaceName; }

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

    // ── Schedule ──────────────────────────────────────────────────────────────
    private final MutableLiveData<List<Map<String, Object>>> schedule = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Object>> nextRace = new MutableLiveData<>();
    private final MutableLiveData<Boolean> scheduleLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> scheduleError = new MutableLiveData<>();

    // ── Season Stats ──────────────────────────────────────────────────────────
    private final MutableLiveData<Map<String, Integer>> dnfMap = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Integer>> podiumMap = new MutableLiveData<>();

    // ── Home Error ────────────────────────────────────────────────────────────
    private final MutableLiveData<String> homeError = new MutableLiveData<>(null);

    public LiveData<String> getHomeError() { return homeError; }
    public void clearHomeError() { homeError.setValue(null); }
    public LiveData<Boolean> getSeasonStarted() { return seasonStarted; }


    // ── Live Session ──────────────────────────────────────────────────────────

    public void fetchLiveSession() {
        liveLoading.setValue(true);
        api.getLiveSession().enqueue(new Callback<LiveSession>() {
            @Override
            public void onResponse(Call<LiveSession> call, Response<LiveSession> response) {
                liveLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    liveSession.setValue(response.body());
                } else {
                    liveError.setValue("No active session found");
                }
            }
            @Override
            public void onFailure(Call<LiveSession> call, Throwable t) {
                liveLoading.setValue(false);
                liveError.setValue("Connection error: " + t.getMessage());
            }
        });
    }


    // ── Results ───────────────────────────────────────────────────────────────

    public void fetchLatestResults(String sessionType, int year) {
        resultsLoading.setValue(true);
        api.getLatestResults(sessionType, year).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call,
                                   Response<Map<String, Object>> response) {
                resultsLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> body = response.body();
                    Object rn = body.get("race_name");
                    if (rn != null) lastRaceName.setValue(rn.toString());
                    raceResults.setValue(parseRaceResults(body));
                } else {
                    resultsError.setValue("Could not load results");
                    homeError.setValue("Could not load data.\nCheck your connection and\nrestart the app if ngrok changed.");
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                resultsLoading.setValue(false);
                homeError.setValue("Could not load data.\nCheck your connection and\nrestart the app if ngrok changed.");
            }
        });
    }

    public void fetchResults(int year, int round, String sessionType) {
        resultsLoading.setValue(true);
        api.getResults(year, round, sessionType).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call,
                                   Response<Map<String, Object>> response) {
                resultsLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    raceResults.setValue(parseRaceResults(response.body()));
                } else {
                    resultsError.setValue("Could not load results");
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                resultsLoading.setValue(false);
                resultsError.setValue("Connection error: " + t.getMessage());
            }
        });
    }

    public void fetchQualifyingResults(int year, int round) {
        resultsLoading.setValue(true);
        qualifyingResults.setValue(null);
        api.getResults(year, round, "Qualifying").enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call,
                                   Response<Map<String, Object>> response) {
                resultsLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        Object results = response.body().get("results");
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
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                resultsLoading.setValue(false);
            }
        });
    }


    // ── Standings ─────────────────────────────────────────────────────────────

    public void fetchDriverStandings(int year) {
        standingsLoading.setValue(true);
        driverStandings.setValue(null);
        api.getDriverStandings(year).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call,
                                   Response<Map<String, Object>> response) {
                standingsLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> body = response.body();
                    Object started = body.get("season_started");
                    if (started instanceof Boolean) {
                        seasonStarted.setValue((Boolean) started);
                    }
                    driverStandings.setValue(parseDriverStandings(body));
                } else {
                    standingsError.setValue("Could not load standings");
                    homeError.setValue("Could not load data.\nCheck your connection and\nrestart the app if ngrok changed.");
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                standingsLoading.setValue(false);
                homeError.setValue("Could not load data.\nCheck your connection and\nrestart the app if ngrok changed.");
            }
        });
    }

    public void fetchConstructorStandings(int year) {
        standingsLoading.setValue(true);
        constructorStandings.setValue(null);
        api.getConstructorStandings(year).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call,
                                   Response<Map<String, Object>> response) {
                standingsLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    constructorStandings.setValue(parseConstructorStandings(response.body()));
                } else {
                    standingsError.setValue("Could not load standings");
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                standingsLoading.setValue(false);
                standingsError.setValue("Connection error: " + t.getMessage());
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
        api.getPitStops(sessionKey).enqueue(new Callback<List<PitStop>>() {
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
        api.getSessionKey(year, round).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call,
                                   Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object keyObj = response.body().get("session_key");
                    if (keyObj != null) {
                        int sessionKey = ((Double) keyObj).intValue();
                        fetchPitStops(sessionKey);
                    } else {
                        pitStopsLoading.setValue(false);
                    }
                } else {
                    pitStopsLoading.setValue(false);
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                pitStopsLoading.setValue(false);
            }
        });
    }


    // ── Schedule ──────────────────────────────────────────────────────────────

    public void fetchSchedule(int year) {
        scheduleLoading.setValue(true);
        api.getScheduleByYear(year).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                scheduleLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    schedule.setValue(response.body());
                } else {
                    scheduleError.setValue("Could not load schedule");
                }
            }
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                scheduleLoading.setValue(false);
                scheduleError.setValue("Connection error: " + t.getMessage());
            }
        });
    }

    public void fetchNextRace() {
        api.getNextRace().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call,
                                   Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    nextRace.setValue(response.body());
                } else {
                    homeError.setValue("Could not load data.\nCheck your connection and\nrestart the app if ngrok changed.");
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                homeError.setValue("Could not load data.\nCheck your connection and\nrestart the app if ngrok changed.");
            }
        });
    }


    // ── Season Stats (DNFs + Podiums) ─────────────────────────────────────────

    public void fetchSeasonStats(int year) {
        api.getScheduleByYear(year).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call,
                                   Response<List<Map<String, Object>>> response) {
                if (!response.isSuccessful() || response.body() == null) return;

                List<Map<String, Object>> races = response.body();
                Map<String, Integer> dnfs    = new java.util.HashMap<>();
                Map<String, Integer> podiums = new java.util.HashMap<>();
                int totalRounds = races.size();
                final int[] completed = {0};

                for (Map<String, Object> race : races) {
                    Object roundObj = race.get("round");
                    if (roundObj == null) continue;
                    int round = ((Double) roundObj).intValue();

                    api.getResults(year, round, "Race").enqueue(
                            new Callback<Map<String, Object>>() {
                                @Override
                                public void onResponse(Call<Map<String, Object>> call,
                                                       Response<Map<String, Object>> response) {
                                    completed[0]++;
                                    if (response.isSuccessful() && response.body() != null) {
                                        List<RaceResult> results = parseRaceResults(response.body());
                                        for (RaceResult result : results) {
                                            if (result.getDriver() == null) continue;
                                            String id = result.getDriver().getDriverId();
                                            if (!result.isFinished() &&
                                                    result.getStatus() != null &&
                                                    !result.getStatus().contains("Lap")) {
                                                dnfs.put(id, dnfs.getOrDefault(id, 0) + 1);
                                            }
                                            try {
                                                int pos = Integer.parseInt(result.getPosition());
                                                if (pos <= 3) {
                                                    podiums.put(id, podiums.getOrDefault(id, 0) + 1);
                                                }
                                            } catch (NumberFormatException ignored) {}
                                        }
                                    }
                                    if (completed[0] >= totalRounds) {
                                        dnfMap.postValue(dnfs);
                                        podiumMap.postValue(podiums);
                                    }
                                }
                                @Override
                                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                    completed[0]++;
                                    if (completed[0] >= totalRounds) {
                                        dnfMap.postValue(dnfs);
                                        podiumMap.postValue(podiums);
                                    }
                                }
                            });
                }
            }
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {}
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
}