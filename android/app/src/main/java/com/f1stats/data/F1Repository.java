package com.f1stats.data;

import android.os.Handler;
import android.os.Looper;

import com.f1stats.api.F1ApiService;
import com.f1stats.db.AppDatabase;
import com.f1stats.db.CachedDriver;
import com.f1stats.db.CachedResult;
import com.f1stats.db.CachedSchedule;
import com.f1stats.db.CachedSessionKey;
import com.f1stats.db.CachedStandings;
import com.f1stats.models.RaceResult;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class F1Repository {

    public interface RepositoryCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    public interface SeasonStatsCallback {
        void onSuccess(Map<String, Integer> dnfs, Map<String, Integer> podiums);
    }

    private static final long ONE_HOUR_MS  = 60 * 60 * 1000L;
    private static final long ONE_DAY_MS   = 24 * ONE_HOUR_MS;

    private final AppDatabase db;
    private final F1ApiService api;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private final int currentYear = Calendar.getInstance().get(Calendar.YEAR);

    public F1Repository(AppDatabase db, F1ApiService api) {
        this.db = db;
        this.api = api;
    }

    // ── Schedule ──────────────────────────────────────────────────────────────

    public void getSchedule(int year, RepositoryCallback<List<Map<String, Object>>> callback) {
        executor.execute(() -> {
            List<CachedSchedule> cached = db.scheduleDao().getByYear(year);
            long now = System.currentTimeMillis();
            boolean isPast  = year < currentYear;
            boolean isFresh = !cached.isEmpty() &&
                    (isPast || (now - cached.get(0).fetchedAt) < ONE_DAY_MS);

            if (isFresh) {
                List<Map<String, Object>> data = schedulesToMaps(cached);
                mainHandler.post(() -> callback.onSuccess(data));
                return;
            }

            mainHandler.post(() ->
                api.getScheduleByYear(year).enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Map<String, Object>> races = response.body();
                            executor.execute(() -> saveSchedule(year, races, System.currentTimeMillis()));
                            callback.onSuccess(races);
                        } else {
                            if (!cached.isEmpty()) {
                                callback.onSuccess(schedulesToMaps(cached));
                            } else {
                                callback.onError("Could not load schedule");
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (!cached.isEmpty()) {
                            callback.onSuccess(schedulesToMaps(cached));
                        } else {
                            callback.onError("Connection error: " + t.getMessage());
                        }
                    }
                })
            );
        });
    }

    private void saveSchedule(int year, List<Map<String, Object>> races, long fetchedAt) {
        List<CachedSchedule> rows = new ArrayList<>();
        for (Map<String, Object> race : races) {
            CachedSchedule row = new CachedSchedule();
            row.year        = year;
            row.round       = toInt(race.get("round"));
            row.raceName    = toStr(race.get("race_name"));
            row.circuit     = toStr(race.get("circuit"));
            row.country     = toStr(race.get("country"));
            row.locality    = toStr(race.get("locality"));
            row.sessionsJson = gson.toJson(race);
            row.fetchedAt   = fetchedAt;
            rows.add(row);
        }
        db.scheduleDao().upsertAll(rows);
    }

    private List<Map<String, Object>> schedulesToMaps(List<CachedSchedule> rows) {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        List<Map<String, Object>> out = new ArrayList<>();
        for (CachedSchedule row : rows) {
            out.add(gson.fromJson(row.sessionsJson, type));
        }
        return out;
    }

    // ── Results ───────────────────────────────────────────────────────────────

    public void getResults(int year, int round, String sessionType,
                           RepositoryCallback<Map<String, Object>> callback) {
        executor.execute(() -> {
            CachedResult cached = db.resultDao().get(year, round, sessionType);
            if (cached != null) {
                Type type = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> data = gson.fromJson(cached.resultsJson, type);
                mainHandler.post(() -> callback.onSuccess(data));
                return;
            }

            mainHandler.post(() ->
                api.getResults(year, round, sessionType).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                                           Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Map<String, Object> body = response.body();
                            executor.execute(() -> {
                                CachedResult row = new CachedResult();
                                row.year        = year;
                                row.round       = round;
                                row.sessionType = sessionType;
                                row.resultsJson = gson.toJson(body);
                                row.fetchedAt   = System.currentTimeMillis();
                                db.resultDao().upsert(row);
                            });
                            callback.onSuccess(body);
                        } else {
                            callback.onError("Could not load results");
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        callback.onError("Connection error: " + t.getMessage());
                    }
                })
            );
        });
    }

    // ── Standings ─────────────────────────────────────────────────────────────

    public void getDriverStandings(int year, RepositoryCallback<Map<String, Object>> callback) {
        getStandings(year, "driver", api.getDriverStandings(year), callback);
    }

    public void getConstructorStandings(int year, RepositoryCallback<Map<String, Object>> callback) {
        getStandings(year, "constructor", api.getConstructorStandings(year), callback);
    }

    private void getStandings(int year, String type, Call<Map<String, Object>> apiCall,
                               RepositoryCallback<Map<String, Object>> callback) {
        executor.execute(() -> {
            CachedStandings cached = db.standingsDao().get(year, type);
            long now = System.currentTimeMillis();
            boolean isPast  = year < currentYear;
            boolean isFresh = cached != null &&
                    (isPast || (now - cached.fetchedAt) < ONE_HOUR_MS);

            if (isFresh) {
                Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> data = gson.fromJson(cached.standingsJson, mapType);
                // Restore season_started so ViewModel parsers see it
                data.put("season_started", cached.seasonStarted);
                mainHandler.post(() -> callback.onSuccess(data));
                return;
            }

            mainHandler.post(() ->
                apiCall.enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                                           Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Map<String, Object> body = response.body();
                            executor.execute(() -> {
                                CachedStandings row = new CachedStandings();
                                row.year         = year;
                                row.type         = type;
                                row.standingsJson = gson.toJson(body);
                                Object started    = body.get("season_started");
                                row.seasonStarted = started instanceof Boolean && (Boolean) started;
                                row.leaderGap    = 0;
                                row.fetchedAt    = System.currentTimeMillis();
                                db.standingsDao().upsert(row);
                            });
                            callback.onSuccess(body);
                        } else {
                            callback.onError("Could not load standings");
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        callback.onError("Connection error: " + t.getMessage());
                    }
                })
            );
        });
    }

    // ── Session Key ───────────────────────────────────────────────────────────

    public void getSessionKey(int year, int round, RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            CachedSessionKey cached = db.sessionKeyDao().get(year, round);
            if (cached != null) {
                mainHandler.post(() -> callback.onSuccess(cached.sessionKey));
                return;
            }

            mainHandler.post(() ->
                api.getSessionKey(year, round).enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                                           Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Object keyObj = response.body().get("session_key");
                            if (keyObj != null) {
                                int sessionKey = ((Double) keyObj).intValue();
                                executor.execute(() -> {
                                    CachedSessionKey row = new CachedSessionKey();
                                    row.year       = year;
                                    row.round      = round;
                                    row.sessionKey = sessionKey;
                                    db.sessionKeyDao().upsert(row);
                                });
                                callback.onSuccess(sessionKey);
                            } else {
                                callback.onError("No session key found");
                            }
                        } else {
                            callback.onError("Could not load session key");
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        callback.onError("Connection error: " + t.getMessage());
                    }
                })
            );
        });
    }

    // ── Season Stats ──────────────────────────────────────────────────────────

    public void fetchSeasonStats(int year, SeasonStatsCallback callback) {
        getSchedule(year, new RepositoryCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> races) {
                executor.execute(() -> {
                    // Partition rounds into cached vs missing
                    List<Integer> missingRounds = new ArrayList<>();
                    List<Map<String, Object>> cachedBodies = new ArrayList<>();

                    Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
                    for (Map<String, Object> race : races) {
                        int round = toInt(race.get("round"));
                        if (round == 0) continue;
                        CachedResult hit = db.resultDao().get(year, round, "Race");
                        if (hit != null) {
                            cachedBodies.add(gson.fromJson(hit.resultsJson, mapType));
                        } else {
                            missingRounds.add(round);
                        }
                    }

                    Map<String, Integer> dnfs    = new HashMap<>();
                    Map<String, Integer> podiums = new HashMap<>();
                    computeStats(cachedBodies, dnfs, podiums);

                    if (missingRounds.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(dnfs, podiums));
                        return;
                    }

                    // Fetch missing rounds in parallel; write to cache as they arrive
                    AtomicInteger pending = new AtomicInteger(missingRounds.size());
                    mainHandler.post(() -> {
                        for (int round : missingRounds) {
                            api.getResults(year, round, "Race").enqueue(
                                new Callback<Map<String, Object>>() {
                                    @Override
                                    public void onResponse(Call<Map<String, Object>> call,
                                                           Response<Map<String, Object>> response) {
                                        if (response.isSuccessful() && response.body() != null) {
                                            Map<String, Object> body = response.body();
                                            executor.execute(() -> {
                                                CachedResult row = new CachedResult();
                                                row.year        = year;
                                                row.round       = round;
                                                row.sessionType = "Race";
                                                row.resultsJson = gson.toJson(body);
                                                row.fetchedAt   = System.currentTimeMillis();
                                                db.resultDao().upsert(row);
                                            });
                                            // Callbacks are on main thread — no sync needed
                                            computeStatsFromBody(body, dnfs, podiums);
                                        }
                                        if (pending.decrementAndGet() == 0) {
                                            callback.onSuccess(dnfs, podiums);
                                        }
                                    }
                                    @Override
                                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                        if (pending.decrementAndGet() == 0) {
                                            callback.onSuccess(dnfs, podiums);
                                        }
                                    }
                                }
                            );
                        }
                    });
                });
            }
            @Override
            public void onError(String error) {
                callback.onSuccess(new HashMap<>(), new HashMap<>());
            }
        });
    }

    // ── Stat helpers ──────────────────────────────────────────────────────────

    private void computeStats(List<Map<String, Object>> bodies,
                              Map<String, Integer> dnfs, Map<String, Integer> podiums) {
        for (Map<String, Object> body : bodies) {
            computeStatsFromBody(body, dnfs, podiums);
        }
    }

    private void computeStatsFromBody(Map<String, Object> body,
                                      Map<String, Integer> dnfs, Map<String, Integer> podiums) {
        Object resultsObj = body.get("results");
        if (!(resultsObj instanceof List)) return;
        try {
            RaceResult[] parsed = gson.fromJson(gson.toJson(resultsObj), RaceResult[].class);
            for (RaceResult r : parsed) {
                if (r.getDriver() == null) continue;
                String id = r.getDriver().getDriverId();
                if (!r.isFinished() && r.getStatus() != null && !r.getStatus().contains("Lap")) {
                    dnfs.put(id, dnfs.getOrDefault(id, 0) + 1);
                }
                try {
                    if (Integer.parseInt(r.getPosition()) <= 3) {
                        podiums.put(id, podiums.getOrDefault(id, 0) + 1);
                    }
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Drivers (headshots) ───────────────────────────────────────────────────

    public void fetchDrivers(int year, RepositoryCallback<List<CachedDriver>> callback) {
        executor.execute(() -> {
            List<CachedDriver> cached = db.driverDao().getBySeason(year);
            if (!cached.isEmpty()) {
                mainHandler.post(() -> callback.onSuccess(cached));
                return;
            }

            mainHandler.post(() ->
                api.getDriversByYear(year).enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Map<String, Object>> raw = response.body();
                            long now = System.currentTimeMillis();
                            executor.execute(() -> {
                                List<CachedDriver> drivers = new ArrayList<>();
                                for (Map<String, Object> d : raw) {
                                    CachedDriver driver = new CachedDriver();
                                    String acronym = toStr(d.get("name_acronym"));
                                    driver.driverId = acronym != null ? acronym.toLowerCase() : "";
                                    driver.code = acronym;
                                    driver.headshotUrl = toStr(d.get("headshot_url"));
                                    driver.teamName = toStr(d.get("team_name"));
                                    driver.teamColour = toStr(d.get("team_colour"));
                                    driver.seasonYear = year;
                                    driver.fetchedAt = now;
                                    String fullName = toStr(d.get("full_name"));
                                    if (fullName != null) {
                                        int sp = fullName.indexOf(' ');
                                        if (sp >= 0) {
                                            driver.firstName = fullName.substring(0, sp);
                                            driver.lastName  = fullName.substring(sp + 1);
                                        } else {
                                            driver.lastName = fullName;
                                        }
                                    }
                                    drivers.add(driver);
                                }
                                db.driverDao().upsertAll(drivers);
                                mainHandler.post(() -> callback.onSuccess(drivers));
                            });
                        } else {
                            callback.onError("Could not load drivers");
                        }
                    }
                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        callback.onError("Connection error: " + t.getMessage());
                    }
                })
            );
        });
    }

    // ── Starting Grid (from Jolpica race results) ─────────────────────────────

    public void getStartingGridFromResults(int year, int round,
                                            RepositoryCallback<List<Map<String, Object>>> callback) {
        getResults(year, round, "Race", new RepositoryCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                executor.execute(() -> {
                    Object resultsObj = data.get("results");
                    if (!(resultsObj instanceof List)) {
                        mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                        return;
                    }
                    try {
                        Type listType = new TypeToken<List<RaceResult>>(){}.getType();
                        List<RaceResult> results = gson.fromJson(gson.toJson(resultsObj), listType);
                        if (results == null) {
                            mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                            return;
                        }

                        List<Map<String, Object>> grid = new ArrayList<>();
                        for (RaceResult r : results) {
                            if (r.getDriver() == null) continue;
                            int gridPos;
                            try { gridPos = Integer.parseInt(r.getGridPosition()); }
                            catch (Exception e) { continue; }
                            if (gridPos <= 0) continue;

                            String code       = r.getDriver().getCode();
                            String teamName   = r.getConstructor() != null ? r.getConstructor().getName() : "";
                            String teamColour = "#FFFFFF";
                            String headshotUrl = null;

                            if (code != null) {
                                CachedDriver cached = db.driverDao().getByCode(code, year);
                                if (cached != null) {
                                    if (cached.teamColour != null) teamColour = cached.teamColour;
                                    headshotUrl = cached.headshotUrl;
                                }
                            }

                            Map<String, Object> entry = new HashMap<>();
                            entry.put("position",      gridPos);
                            entry.put("driver_number", r.getDriverNumber() != null ? r.getDriverNumber() : "");
                            entry.put("name_acronym",  code != null ? code : "???");
                            entry.put("full_name",     r.getDriver().getFullName() != null ? r.getDriver().getFullName() : "");
                            entry.put("team_name",     teamName != null ? teamName : "");
                            entry.put("team_colour",   teamColour);
                            entry.put("headshot_url",  headshotUrl);
                            grid.add(entry);
                        }

                        grid.sort((a, b) -> {
                            int pa = a.get("position") instanceof Number ? ((Number) a.get("position")).intValue() : 99;
                            int pb = b.get("position") instanceof Number ? ((Number) b.get("position")).intValue() : 99;
                            return Integer.compare(pa, pb);
                        });

                        mainHandler.post(() -> callback.onSuccess(grid));
                    } catch (Exception e) {
                        android.util.Log.e("F1Repository", "Failed to build starting grid", e);
                        mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
                    }
                });
            }
            @Override
            public void onError(String error) {
                android.util.Log.e("F1Repository", "Race results error for grid: " + error);
                callback.onError(error);
            }
        });
    }

    // ── Ensure all Race results cached for season ─────────────────────────────

    public void ensureSeasonResultsCached(int year, RepositoryCallback<Void> callback) {
        getSchedule(year, new RepositoryCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> races) {
                executor.execute(() -> {
                    List<Integer> missingRounds = new ArrayList<>();
                    for (Map<String, Object> race : races) {
                        int round = toInt(race.get("round"));
                        if (round == 0) continue;
                        CachedResult hit = db.resultDao().get(year, round, "Race");
                        if (hit == null) missingRounds.add(round);
                    }

                    if (missingRounds.isEmpty()) {
                        mainHandler.post(() -> callback.onSuccess(null));
                        return;
                    }

                    AtomicInteger pending = new AtomicInteger(missingRounds.size());
                    mainHandler.post(() -> {
                        for (int round : missingRounds) {
                            api.getResults(year, round, "Race").enqueue(
                                new Callback<Map<String, Object>>() {
                                    @Override
                                    public void onResponse(Call<Map<String, Object>> call,
                                                           Response<Map<String, Object>> response) {
                                        if (response.isSuccessful() && response.body() != null) {
                                            Map<String, Object> body = response.body();
                                            executor.execute(() -> {
                                                CachedResult row = new CachedResult();
                                                row.year        = year;
                                                row.round       = round;
                                                row.sessionType = "Race";
                                                row.resultsJson = gson.toJson(body);
                                                row.fetchedAt   = System.currentTimeMillis();
                                                db.resultDao().upsert(row);
                                            });
                                        }
                                        if (pending.decrementAndGet() == 0) {
                                            mainHandler.post(() -> callback.onSuccess(null));
                                        }
                                    }
                                    @Override
                                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                        if (pending.decrementAndGet() == 0) {
                                            mainHandler.post(() -> callback.onSuccess(null));
                                        }
                                    }
                                }
                            );
                        }
                    });
                });
            }
            @Override
            public void onError(String error) {
                callback.onSuccess(null);
            }
        });
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private int toInt(Object val) {
        if (val instanceof Double)  return ((Double) val).intValue();
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof String)  {
            try { return Integer.parseInt((String) val); } catch (Exception ignored) {}
        }
        return 0;
    }

    private String toStr(Object val) {
        return val != null ? val.toString() : null;
    }
}
