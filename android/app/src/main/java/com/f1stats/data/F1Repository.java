package com.f1stats.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.f1stats.util.DebugLog;
import com.f1stats.util.HeadToHead;
import com.f1stats.DateHelper;
import com.f1stats.api.F1ApiClient;
import com.f1stats.api.F1ApiService;
import com.f1stats.db.AppDatabase;
import com.f1stats.db.CachedCircuitStats;
import com.f1stats.db.CachedDriver;
import com.f1stats.db.CachedMeeting;
import com.f1stats.db.CachedResult;
import com.f1stats.db.CachedSchedule;
import com.f1stats.db.CachedSessionKey;
import com.f1stats.db.CachedStandings;
import com.f1stats.models.CircuitStatsResponse;
import com.f1stats.models.NewsResponse;
import com.f1stats.models.OnThisDayResponse;
import com.f1stats.models.RaceResult;
import com.f1stats.models.WeatherForecast;
import com.f1stats.util.ResultStatus;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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

    private static final long THREE_HOURS_MS = 3 * ONE_HOUR_MS;
    // Results can still change (post-race penalties) for this long after a session ends
    private static final long RESULTS_SETTLE_MS = 72 * ONE_HOUR_MS;

    private static F1Repository instance;

    private final Context appContext;
    private final AppDatabase db;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    // In-flight getResults requests keyed by year/round/sessionType; guarded by itself
    private final Map<String, List<RepositoryCallback<Map<String, Object>>>> inFlightResults = new HashMap<>();

    public static synchronized F1Repository getInstance(Context ctx) {
        if (instance == null) {
            instance = new F1Repository(ctx.getApplicationContext());
        }
        return instance;
    }

    private F1Repository(Context appContext) {
        this.appContext = appContext;
        this.db = AppDatabase.getInstance(appContext);
    }

    // Resolved per call so a base-URL change in Settings (F1ApiClient.reset) applies immediately
    private F1ApiService api() {
        return F1ApiClient.getInstance(appContext).getService();
    }

    // Not cached in a field: the singleton can outlive a year boundary
    private int currentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    // ── Schedule ──────────────────────────────────────────────────────────────

    public void getSchedule(int year, RepositoryCallback<List<Map<String, Object>>> callback) {
        executor.execute(() -> {
            List<CachedSchedule> cached = db.scheduleDao().getByYear(year);
            long now = System.currentTimeMillis();
            boolean isPast  = year < currentYear();
            boolean isFresh = !cached.isEmpty() &&
                    (isPast || (now - cached.get(0).fetchedAt) < ONE_DAY_MS);

            if (isFresh) {
                List<Map<String, Object>> data = schedulesToMaps(cached);
                mainHandler.post(() -> callback.onSuccess(data));
                return;
            }

            mainHandler.post(() ->
                api().getScheduleByYear(year).enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Map<String, Object>> races = response.body();
                            // Serialize each map on the calling (main) thread before the executor
                            // starts — Gson's LinkedTreeMap is not thread-safe, and passing the
                            // live references to both threads causes ConcurrentModificationException.
                            long fetchedAt = System.currentTimeMillis();
                            List<String> snapshots = new ArrayList<>(races.size());
                            for (Map<String, Object> race : races) snapshots.add(gson.toJson(race));
                            executor.execute(() -> saveSchedule(year, snapshots, fetchedAt));
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

    private void saveSchedule(int year, List<String> snapshots, long fetchedAt) {
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        List<CachedSchedule> rows = new ArrayList<>();
        for (String json : snapshots) {
            Map<String, Object> race = gson.fromJson(json, mapType);
            CachedSchedule row = new CachedSchedule();
            row.year         = year;
            row.round        = toInt(race.get("round"));
            row.raceName     = toStr(race.get("race_name"));
            row.circuit      = toStr(race.get("circuit"));
            row.country      = toStr(race.get("country"));
            row.locality     = toStr(race.get("locality"));
            row.sessionsJson = json;
            row.fetchedAt    = fetchedAt;
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
            long now = System.currentTimeMillis();
            Long sessionTime = getSessionTimeUtcMillis(year, round, sessionType);
            CachedResult cached = db.resultDao().get(year, round, sessionType);
            if (cached != null && isResultRowFresh(cached, year, sessionTime, now)) {
                Map<String, Object> data = parseResultRow(cached);
                mainHandler.post(() -> callback.onSuccess(data));
                return;
            }
            requestResults(year, round, sessionType, sessionTime, callback);
        });
    }

    /**
     * Fetches results from the network, deduplicating concurrent requests for the same
     * year/round/session. Stores the response only when the caching rules allow it, and
     * falls back to any stored row on failure. All callbacks fire on the main thread after
     * the Room write, so callers reading Room afterwards see the new row.
     */
    @WorkerThread
    private void requestResults(int year, int round, String sessionType, @Nullable Long sessionTime,
                                RepositoryCallback<Map<String, Object>> callback) {
        String key = year + "/" + round + "/" + sessionType;
        synchronized (inFlightResults) {
            List<RepositoryCallback<Map<String, Object>>> waiting = inFlightResults.get(key);
            if (waiting != null) {
                waiting.add(callback);
                return;
            }
            List<RepositoryCallback<Map<String, Object>>> first = new ArrayList<>();
            first.add(callback);
            inFlightResults.put(key, first);
        }

        mainHandler.post(() ->
            api().getResults(year, round, sessionType).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call,
                                       Response<Map<String, Object>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Map<String, Object> body = response.body();
                        // Snapshot on the main thread — Gson's LinkedTreeMap is not thread-safe
                        String json = gson.toJson(body);
                        int count = countResults(body);
                        executor.execute(() -> {
                            boolean stored = maybeStoreResult(year, round, sessionType, json, count,
                                    sessionTime, System.currentTimeMillis());
                            if (!stored && count <= 0) {
                                // Empty refetch (e.g. API hiccup) — prefer a stored non-empty row
                                CachedResult existing = db.resultDao().get(year, round, sessionType);
                                if (existing != null && hasResults(existing.resultsJson)) {
                                    completeResults(key, parseResultRow(existing), null);
                                    return;
                                }
                            }
                            mainHandler.post(() -> deliverResults(key, body, null));
                        });
                    } else {
                        failResults(key, year, round, sessionType,
                                "Could not load results (HTTP " + response.code() + ")");
                    }
                }
                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    failResults(key, year, round, sessionType, "Connection error: " + t.getMessage());
                }
            })
        );
    }

    private void failResults(String key, int year, int round, String sessionType, String error) {
        executor.execute(() -> {
            CachedResult existing = db.resultDao().get(year, round, sessionType);
            completeResults(key, existing != null ? parseResultRow(existing) : null, error);
        });
    }

    private void completeResults(String key, @Nullable Map<String, Object> data, @Nullable String error) {
        mainHandler.post(() -> deliverResults(key, data, error));
    }

    // Main thread only. Delivers data if present, otherwise the error.
    private void deliverResults(String key, @Nullable Map<String, Object> data, @Nullable String error) {
        List<RepositoryCallback<Map<String, Object>>> waiting;
        synchronized (inFlightResults) {
            waiting = inFlightResults.remove(key);
        }
        if (waiting == null) return;
        for (RepositoryCallback<Map<String, Object>> cb : waiting) {
            if (data != null) cb.onSuccess(data);
            else cb.onError(error != null ? error : "Could not load results");
        }
    }

    /** Inserts a results row only if it is non-empty and the session has ended. */
    @WorkerThread
    private boolean maybeStoreResult(int year, int round, String sessionType, String json, int count,
                                     @Nullable Long sessionTime, long now) {
        if (count <= 0 || !sessionEnded(year, sessionTime, now)) return false;
        CachedResult row = new CachedResult();
        row.year        = year;
        row.round       = round;
        row.sessionType = sessionType;
        row.resultsJson = json;
        row.fetchedAt   = now;
        db.resultDao().upsert(row);
        return true;
    }

    private boolean sessionEnded(int year, @Nullable Long sessionTime, long now) {
        if (year < currentYear()) return true;
        return sessionTime != null && sessionTime + THREE_HOURS_MS < now;
    }

    /**
     * A stored row is permanent unless the session ended less than 72h ago, in which case it
     * expires after 1h so post-race penalties are picked up. Unknown session time in the
     * current season is treated as recent.
     */
    private boolean isResultRowFresh(CachedResult row, int year, @Nullable Long sessionTime, long now) {
        if (!hasResults(row.resultsJson)) return false;
        if (year < currentYear()) return true;
        boolean recent = sessionTime == null || now - (sessionTime + THREE_HOURS_MS) < RESULTS_SETTLE_MS;
        return !recent || now - row.fetchedAt < ONE_HOUR_MS;
    }

    private Map<String, Object> parseResultRow(CachedResult row) {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(row.resultsJson, type);
        return data != null ? data : new HashMap<>();
    }

    private int countResults(Map<String, Object> body) {
        Object resultsObj = body.get("results");
        return resultsObj instanceof List ? ((List<?>) resultsObj).size() : 0;
    }

    private boolean hasResults(@Nullable String resultsJson) {
        if (resultsJson == null) return false;
        try {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> data = gson.fromJson(resultsJson, type);
            return data != null && countResults(data) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * UTC start time of a session from the Room schedule, or null if unknown.
     * Session names: "Race", "Qualifying", "Sprint", "Sprint Qualifying".
     */
    @WorkerThread
    @Nullable
    Long getSessionTimeUtcMillis(int year, int round, String sessionType) {
        CachedSchedule row = db.scheduleDao().get(year, round);
        if (row == null || row.sessionsJson == null) return null;
        try {
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> race = gson.fromJson(row.sessionsJson, mapType);
            return race != null ? sessionTimeFromRace(race, sessionType) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private Long sessionTimeFromRace(Map<String, Object> race, String sessionType) {
        Object sessObj = race.get("sessions");
        if (!(sessObj instanceof List)) return null;
        for (Object s : (List<?>) sessObj) {
            if (!(s instanceof Map)) continue;
            Map<?, ?> session = (Map<?, ?>) s;
            if (sessionType.equals(session.get("name"))) {
                long t = DateHelper.toMillis(toStr(session.get("datetime")));
                return t > 0 ? t : null;
            }
        }
        return null;
    }

    private boolean hasSession(Map<String, Object> race, String sessionType) {
        Object sessObj = race.get("sessions");
        if (!(sessObj instanceof List)) return false;
        for (Object s : (List<?>) sessObj) {
            if (s instanceof Map && sessionType.equals(((Map<?, ?>) s).get("name"))) return true;
        }
        return false;
    }

    // ── Standings ─────────────────────────────────────────────────────────────

    public void getDriverStandings(int year, RepositoryCallback<Map<String, Object>> callback) {
        getStandings(year, "driver", api().getDriverStandings(year), callback);
    }

    public void getConstructorStandings(int year, RepositoryCallback<Map<String, Object>> callback) {
        getStandings(year, "constructor", api().getConstructorStandings(year), callback);
    }

    private void getStandings(int year, String type, Call<Map<String, Object>> apiCall,
                               RepositoryCallback<Map<String, Object>> callback) {
        executor.execute(() -> {
            CachedStandings cached = db.standingsDao().get(year, type);
            long now = System.currentTimeMillis();
            boolean isPast  = year < currentYear();
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
                api().getSessionKey(year, round).enqueue(new Callback<Map<String, Object>>() {
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
                executor.execute(() -> cacheMissingSeasonResults(year, races, () ->
                    executor.execute(() -> {
                        List<Map<String, Object>> bodies = new ArrayList<>();
                        for (Map<String, Object> race : races) {
                            int round = toInt(race.get("round"));
                            if (round == 0) continue;
                            CachedResult hit = db.resultDao().get(year, round, "Race");
                            if (hit != null && hasResults(hit.resultsJson)) {
                                bodies.add(parseResultRow(hit));
                            }
                        }
                        Map<String, Integer> dnfs    = new HashMap<>();
                        Map<String, Integer> podiums = new HashMap<>();
                        computeStats(bodies, dnfs, podiums);
                        mainHandler.post(() -> callback.onSuccess(dnfs, podiums));
                    })
                ));
            }
            @Override
            public void onError(String error) {
                callback.onSuccess(new HashMap<>(), new HashMap<>());
            }
        });
    }

    /**
     * Fetches and stores results for every session of the season that has ended but has no
     * non-empty Room row: the Race of each round, plus the Sprint for sprint weekends.
     * Sessions that haven't happened yet are never requested. onDone runs on the main thread
     * once every request has completed and been written.
     */
    @WorkerThread
    private void cacheMissingSeasonResults(int year, List<Map<String, Object>> races, Runnable onDone) {
        long now = System.currentTimeMillis();
        List<Integer> missingRounds  = new ArrayList<>();
        List<Integer> missingSprints = new ArrayList<>();
        List<Integer> futureRounds   = new ArrayList<>();
        Map<String, Long> targets = new LinkedHashMap<>();   // "round/sessionType" -> session time

        for (Map<String, Object> race : races) {
            int round = toInt(race.get("round"));
            if (round == 0) continue;

            Long raceTime = sessionTimeFromRace(race, "Race");
            if (sessionEnded(year, raceTime, now)) {
                CachedResult hit = db.resultDao().get(year, round, "Race");
                if (hit == null || !hasResults(hit.resultsJson)) {
                    missingRounds.add(round);
                    targets.put(round + "/Race", raceTime);
                }
            } else {
                futureRounds.add(round);
            }

            if (hasSession(race, "Sprint")) {
                Long sprintTime = sessionTimeFromRace(race, "Sprint");
                if (sessionEnded(year, sprintTime, now)) {
                    CachedResult hit = db.resultDao().get(year, round, "Sprint");
                    if (hit == null || !hasResults(hit.resultsJson)) {
                        missingSprints.add(round);
                        targets.put(round + "/Sprint", sprintTime);
                    }
                }
            }
        }

        DebugLog.d("H2H_DEBUG", "  missingRounds=" + missingRounds.size() + " " + missingRounds
                + " missingSprints=" + missingSprints);
        DebugLog.d("H2H_DEBUG", "  skipped future rounds=" + futureRounds.size() + " " + futureRounds);

        if (targets.isEmpty()) {
            DebugLog.d("H2H_DEBUG", "  all rounds already cached — firing callback immediately");
            mainHandler.post(onDone);
            return;
        }

        AtomicInteger pending = new AtomicInteger(targets.size());
        for (Map.Entry<String, Long> target : targets.entrySet()) {
            String[] parts = target.getKey().split("/");
            int round = Integer.parseInt(parts[0]);
            String sessionType = parts[1];
            requestResults(year, round, sessionType, target.getValue(),
                new RepositoryCallback<Map<String, Object>>() {
                    @Override
                    public void onSuccess(Map<String, Object> body) {
                        int remaining = pending.decrementAndGet();
                        DebugLog.d("H2H_DEBUG", "  fetched round=" + round + " session=" + sessionType
                                + " resultCount=" + countResults(body) + " remaining=" + remaining);
                        if (remaining == 0) onDone.run();
                    }
                    @Override
                    public void onError(String error) {
                        int remaining = pending.decrementAndGet();
                        DebugLog.d("H2H_DEBUG", "  round=" + round + " session=" + sessionType
                                + " fetch FAILED: " + error + " remaining=" + remaining);
                        if (remaining == 0) onDone.run();
                    }
                });
        }
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
                if (ResultStatus.isDnf(r.getStatus())) {
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

    /** OpenF1 drivers for a season (2023+): headshots and team colours. Ids are synthetic. */
    public void fetchDrivers(int year, RepositoryCallback<List<CachedDriver>> callback) {
        executor.execute(() -> {
            List<CachedDriver> cached = db.driverDao().getBySeason(year);
            if (!cached.isEmpty()) {
                mainHandler.post(() -> callback.onSuccess(cached));
                return;
            }

            mainHandler.post(() ->
                api().getDriversByYear(year).enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Map<String, Object>> raw = response.body();
                            executor.execute(() -> {
                                List<CachedDriver> drivers = parseOpenF1Drivers(raw, year);
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

    // ── Drivers for a season (Jolpica identity, OpenF1 enrichment) ────────────

    /**
     * Drivers for the picker / H2H, built from Jolpica data for every season so each item
     * carries the Jolpica driverId (e.g. "russell"), code, permanentNumber and names.
     * Source: driver standings, falling back to the season's race results. For 2023+ the
     * OpenF1 headshot and team colour are attached by code, then by driver number.
     */
    public void fetchDriversForSeason(int year, RepositoryCallback<List<CachedDriver>> callback) {
        getDriverStandings(year, new RepositoryCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> body) {
                String json = gson.toJson(body);
                executor.execute(() -> {
                    List<CachedDriver> drivers = parseDriversFromStandingsJson(json, year);
                    if (!drivers.isEmpty()) {
                        attachOpenF1Details(year, drivers, callback);
                    } else {
                        driversFromSeasonResults(year, callback);
                    }
                });
            }
            @Override
            public void onError(String error) {
                driversFromSeasonResults(year, callback);
            }
        });
    }

    private void driversFromSeasonResults(int year, RepositoryCallback<List<CachedDriver>> callback) {
        ensureSeasonResultsCached(year, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                executor.execute(() -> attachOpenF1Details(year,
                        parseDriversFromResultRows(db.resultDao().getByYear(year), year), callback));
            }
            @Override
            public void onError(String error) {
                onSuccess(null);
            }
        });
    }

    @WorkerThread
    private void attachOpenF1Details(int year, List<CachedDriver> drivers,
                                     RepositoryCallback<List<CachedDriver>> callback) {
        if (year < 2023 || drivers.isEmpty()) {
            mainHandler.post(() -> callback.onSuccess(drivers));
            return;
        }
        fetchDrivers(year, new RepositoryCallback<List<CachedDriver>>() {
            @Override
            public void onSuccess(List<CachedDriver> openF1) {
                for (CachedDriver d : drivers) {
                    CachedDriver match = null;
                    for (CachedDriver o : openF1) {
                        if (d.code != null && d.code.equalsIgnoreCase(o.code)) { match = o; break; }
                    }
                    if (match == null && d.permanentNumber != null) {
                        for (CachedDriver o : openF1) {
                            if (d.permanentNumber.equals(o.permanentNumber)) { match = o; break; }
                        }
                    }
                    if (match != null) {
                        d.headshotUrl = match.headshotUrl;
                        d.teamColour  = match.teamColour != null && !match.teamColour.startsWith("#")
                                ? "#" + match.teamColour : match.teamColour;
                        if (d.teamName == null) d.teamName = match.teamName;
                    }
                }
                callback.onSuccess(drivers);
            }
            @Override
            public void onError(String error) {
                callback.onSuccess(drivers);
            }
        });
    }

    private List<CachedDriver> parseOpenF1Drivers(List<Map<String, Object>> raw, int year) {
        List<CachedDriver> drivers = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map<String, Object> d : raw) {
            CachedDriver driver = new CachedDriver();
            String acronym = toStr(d.get("name_acronym"));
            driver.driverId    = acronym != null ? acronym.toLowerCase() : "";
            driver.code        = acronym;
            int number         = toInt(d.get("driver_number"));
            driver.permanentNumber = number > 0 ? String.valueOf(number) : null;
            driver.headshotUrl = toStr(d.get("headshot_url"));
            driver.teamName    = toStr(d.get("team_name"));
            driver.teamColour  = toStr(d.get("team_colour"));
            driver.seasonYear  = year;
            driver.fetchedAt   = now;
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
        return drivers;
    }

    @SuppressWarnings("unchecked")
    private List<CachedDriver> parseDriversFromStandingsJson(String standingsJson, int year) {
        List<CachedDriver> result = new ArrayList<>();
        try {
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> body = gson.fromJson(standingsJson, mapType);
            Object standingsObj = body.get("standings");
            if (!(standingsObj instanceof List)) return result;

            for (Object entry : (List<?>) standingsObj) {
                if (!(entry instanceof Map)) continue;
                Map<String, Object> standing = (Map<String, Object>) entry;
                Object driverObj = standing.get("Driver");
                if (!(driverObj instanceof Map)) continue;
                Map<String, Object> d = (Map<String, Object>) driverObj;

                String teamName = null;
                Object constructorsObj = standing.get("Constructors");
                if (constructorsObj instanceof List) {
                    List<?> constrs = (List<?>) constructorsObj;
                    if (!constrs.isEmpty() && constrs.get(0) instanceof Map) {
                        teamName = toStr(((Map<?, ?>) constrs.get(0)).get("name"));
                    }
                }

                CachedDriver driver = new CachedDriver();
                String driverId = toStr(d.get("driverId"));
                driver.driverId        = driverId != null ? driverId : "";
                driver.code            = toStr(d.get("code"));
                driver.firstName       = toStr(d.get("givenName"));
                driver.lastName        = toStr(d.get("familyName"));
                driver.nationality     = toStr(d.get("nationality"));
                driver.dateOfBirth     = toStr(d.get("dateOfBirth"));
                driver.permanentNumber = toStr(d.get("permanentNumber"));
                driver.teamName        = teamName;
                driver.seasonYear      = year;
                result.add(driver);
            }
        } catch (Exception e) {
            android.util.Log.e("F1Repository", "Failed to parse drivers from standings", e);
        }
        return result;
    }

    /** Unique drivers across a season's stored results; team comes from the latest round. */
    private List<CachedDriver> parseDriversFromResultRows(List<CachedResult> rows, int year) {
        rows = new ArrayList<>(rows);
        rows.sort((a, b) -> Integer.compare(a.round, b.round));
        Map<String, CachedDriver> byId = new LinkedHashMap<>();
        for (CachedResult row : rows) {
            Object resultsObj = parseResultRow(row).get("results");
            if (!(resultsObj instanceof List)) continue;
            RaceResult[] parsed;
            try {
                parsed = gson.fromJson(gson.toJson(resultsObj), RaceResult[].class);
            } catch (Exception e) {
                continue;
            }
            if (parsed == null) continue;
            for (RaceResult r : parsed) {
                RaceResult.Driver rd = r.getDriver();
                if (rd == null || rd.getDriverId() == null) continue;
                CachedDriver driver = byId.get(rd.getDriverId());
                if (driver == null) {
                    driver = new CachedDriver();
                    driver.driverId        = rd.getDriverId();
                    driver.code            = rd.getCode();
                    driver.firstName       = rd.getFirstName();
                    driver.lastName        = rd.getLastName();
                    driver.nationality     = rd.getNationality();
                    driver.permanentNumber = rd.getNumber();
                    driver.seasonYear      = year;
                    byId.put(driver.driverId, driver);
                }
                if (r.getConstructor() != null) driver.teamName = r.getConstructor().getName();
            }
        }
        return new ArrayList<>(byId.values());
    }

    // ── Meetings (cache-first) ────────────────────────────────────────────────

    public void getMeetings(int year, RepositoryCallback<List<Map<String, Object>>> callback) {
        executor.execute(() -> {
            List<CachedMeeting> cached = db.meetingDao().getByYear(year);
            long now = System.currentTimeMillis();
            boolean isPast  = year < currentYear();
            boolean isFresh = !cached.isEmpty() &&
                    (isPast || (now - cached.get(0).fetchedAt) < 7 * ONE_DAY_MS);

            if (isFresh) {
                mainHandler.post(() -> callback.onSuccess(meetingsToMaps(cached)));
                return;
            }

            mainHandler.post(() ->
                api().getMeetings(year).enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Map<String, Object>> meetings = response.body();
                            executor.execute(() -> saveMeetings(year, meetings));
                            callback.onSuccess(meetings);
                        } else if (!cached.isEmpty()) {
                            callback.onSuccess(meetingsToMaps(cached));
                        } else {
                            callback.onError("Could not load meetings");
                        }
                    }
                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (!cached.isEmpty()) {
                            callback.onSuccess(meetingsToMaps(cached));
                        } else {
                            callback.onError("Connection error: " + t.getMessage());
                        }
                    }
                })
            );
        });
    }

    private void saveMeetings(int year, List<Map<String, Object>> meetings) {
        List<CachedMeeting> rows = new ArrayList<>();
        for (int i = 0; i < meetings.size(); i++) {
            Map<String, Object> m = meetings.get(i);
            CachedMeeting row = new CachedMeeting();
            Object keyObj = m.get("meeting_key");
            row.meetingKey     = keyObj instanceof Number ? ((Number) keyObj).intValue()
                                                          : year * 1000 + i;
            row.year           = year;
            row.meetingName    = toStr(m.get("meeting_name"));
            row.location       = toStr(m.get("location"));
            row.countryName    = toStr(m.get("country_name"));
            row.countryFlagUrl = toStr(m.get("country_flag"));
            row.circuitImageUrl = toStr(m.get("circuit_image"));
            row.dateStart      = toStr(m.get("date_start"));
            row.fetchedAt      = System.currentTimeMillis();
            rows.add(row);
        }
        db.meetingDao().upsertAll(rows);
    }

    private List<Map<String, Object>> meetingsToMaps(List<CachedMeeting> meetings) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CachedMeeting m : meetings) {
            Map<String, Object> map = new HashMap<>();
            map.put("meeting_name",  m.meetingName);
            map.put("circuit_image", m.circuitImageUrl);
            map.put("country_flag",  m.countryFlagUrl);
            map.put("location",      m.location);
            map.put("country_name",  m.countryName);
            map.put("date_start",    m.dateStart);
            out.add(map);
        }
        return out;
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

    // ── Ensure all Race (and Sprint) results cached for season ────────────────

    public void ensureSeasonResultsCached(int year, RepositoryCallback<Void> callback) {
        DebugLog.d("H2H_DEBUG", "ensureSeasonResultsCached: year=" + year);
        getSchedule(year, new RepositoryCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> races) {
                executor.execute(() -> {
                    DebugLog.d("H2H_DEBUG", "  schedule returned " + races.size() + " races");
                    cacheMissingSeasonResults(year, races, () -> callback.onSuccess(null));
                });
            }
            @Override
            public void onError(String error) {
                DebugLog.d("H2H_DEBUG", "ensureSeasonResultsCached schedule error: " + error);
                callback.onSuccess(null);
            }
        });
    }

    /**
     * The season's Race and Sprint results, parsed for {@link HeadToHead}. Fetches any missing
     * rounds first (see {@link #ensureSeasonResultsCached}); never fails, so a season with
     * nothing cached comes back empty.
     */
    public void getSeasonResults(int year, RepositoryCallback<HeadToHead.Season> callback) {
        ensureSeasonResultsCached(year, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                executor.execute(() -> {
                    HeadToHead.Season season = new HeadToHead.Season(year);
                    for (CachedResult row : db.resultDao().getByYear(year)) {
                        season.addResultsJson(row.round, row.sessionType, row.resultsJson, gson);
                    }
                    mainHandler.post(() -> callback.onSuccess(season));
                });
            }
            @Override
            public void onError(String error) {
                onSuccess(null);
            }
        });
    }

    // ── Circuit Stats ─────────────────────────────────────────────────────────

    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;

    public void getCircuitStats(String circuitId, RepositoryCallback<CircuitStatsResponse> callback) {
        executor.execute(() -> {
            DebugLog.d("TRACK_STATS", "Loading circuit stats for: " + circuitId);
            CachedCircuitStats cached = db.circuitStatsDao().get(circuitId);
            if (cached != null) {
                long age = System.currentTimeMillis() - cached.cachedAt;
                CircuitStatsResponse response = gson.fromJson(cached.jsonData, CircuitStatsResponse.class);
                // A circuit that hosted a GP this season can gain a race any weekend
                long ttl = response != null && response.lastGPYear == currentYear() ? ONE_DAY_MS : SEVEN_DAYS_MS;
                DebugLog.d("TRACK_STATS", "Cache hit: true, age=" + (age / 1000) + "s, ttl=" + (ttl / 1000) + "s");
                // totalRaces == 0 means the backend had no data — treat as a miss
                if (response != null && response.totalRaces > 0 && age < ttl) {
                    mainHandler.post(() -> callback.onSuccess(response));
                    return;
                }
            }
            DebugLog.d("TRACK_STATS", "Cache hit: false — fetching from backend");

            mainHandler.post(() ->
                api().getCircuitStats(circuitId).enqueue(new retrofit2.Callback<CircuitStatsResponse>() {
                    @Override
                    public void onResponse(retrofit2.Call<CircuitStatsResponse> call,
                                           retrofit2.Response<CircuitStatsResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            CircuitStatsResponse stats = response.body();
                            DebugLog.d("TRACK_STATS", "Stats received: totalRaces=" + stats.totalRaces
                                    + " mostWins=" + (stats.mostWins != null ? stats.mostWins.name + "(" + stats.mostWins.count + ")" : "null"));
                            if (stats.totalRaces > 0) executor.execute(() -> {
                                CachedCircuitStats entity = new CachedCircuitStats();
                                entity.circuitId = circuitId;
                                entity.jsonData  = gson.toJson(stats);
                                entity.cachedAt  = System.currentTimeMillis();
                                db.circuitStatsDao().upsert(entity);
                            });
                            callback.onSuccess(stats);
                        } else {
                            callback.onError("Failed to load circuit stats (HTTP " + response.code() + ")");
                        }
                    }
                    @Override
                    public void onFailure(retrofit2.Call<CircuitStatsResponse> call, Throwable t) {
                        callback.onError("Connection error: " + t.getMessage());
                    }
                })
            );
        });
    }

    // ── Weather forecast, news, history (memory cache only) ───────────────────

    /** The backend's default "this week" window: ±3 days around the date. */
    private static final int HISTORY_WINDOW_DAYS = 3;
    private static final long NEWS_TTL_MS = 15 * 60 * 1000L;

    private final MemoryCache<WeatherForecast> weatherCache = new MemoryCache<>(ONE_HOUR_MS);
    private final MemoryCache<NewsResponse> newsCache = new MemoryCache<>(NEWS_TTL_MS);
    // Keyed by date: valid until the date changes
    private final MemoryCache<OnThisDayResponse> historyCache = new MemoryCache<>(Long.MAX_VALUE);

    /** Race weekend forecast; cached for 1 hour. */
    public void getWeatherForecast(int year, int round, boolean forceRefresh,
                                   RepositoryCallback<WeatherForecast> callback) {
        fetchMemoryCached(weatherCache, year + "/" + round, forceRefresh,
                () -> api().getWeatherForecast(year, round), "forecast", callback);
    }

    /** Latest headlines, newest first; cached for 15 minutes per limit. */
    public void getNews(int limit, boolean forceRefresh, RepositoryCallback<NewsResponse> callback) {
        fetchMemoryCached(newsCache, String.valueOf(limit), forceRefresh,
                () -> api().getNews(limit), "news", callback);
    }

    /** Race winners from this week in past seasons; cached until the date changes. */
    public void getOnThisDay(String dateIso, RepositoryCallback<OnThisDayResponse> callback) {
        historyCache.retainOnly(dateIso);
        fetchMemoryCached(historyCache, dateIso, false,
                () -> api().getOnThisDay(dateIso, HISTORY_WINDOW_DAYS), "history", callback);
    }

    /**
     * Serves a fresh cached value, otherwise fetches. A failed fetch falls back to the last
     * good value for the key, if there is one. Callbacks always run on the main thread.
     */
    private <T> void fetchMemoryCached(MemoryCache<T> cache, String key, boolean forceRefresh,
                                       Supplier<Call<T>> request, String what,
                                       RepositoryCallback<T> callback) {
        T fresh = forceRefresh ? null : cache.getFresh(key, System.currentTimeMillis());
        if (fresh != null) {
            mainHandler.post(() -> callback.onSuccess(fresh));
            return;
        }
        mainHandler.post(() -> request.get().enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                T body = response.body();
                if (response.isSuccessful() && body != null) {
                    cache.put(key, body, System.currentTimeMillis());
                    callback.onSuccess(body);
                } else {
                    deliverLastGood(cache, key, "Could not load " + what
                            + " (HTTP " + response.code() + ")", callback);
                }
            }

            @Override
            public void onFailure(Call<T> call, Throwable t) {
                deliverLastGood(cache, key, "Connection error: " + t.getMessage(), callback);
            }
        }));
    }

    private static <T> void deliverLastGood(MemoryCache<T> cache, String key, String error,
                                            RepositoryCallback<T> callback) {
        T last = cache.getLast(key);
        DebugLog.d("F1Repository", error + (last != null ? "; serving last good value" : ""));
        if (last != null) callback.onSuccess(last);
        else callback.onError(error);
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
