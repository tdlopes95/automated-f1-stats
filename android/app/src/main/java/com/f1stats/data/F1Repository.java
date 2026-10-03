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
import com.f1stats.db.CachedJson;
import com.f1stats.db.CachedTrackMap;
import com.f1stats.db.CachedDriver;
import com.f1stats.db.CachedMeeting;
import com.f1stats.db.CachedRaceAnalysis;
import com.f1stats.db.CachedResult;
import com.f1stats.db.CachedSchedule;
import com.f1stats.db.CachedSessionKey;
import com.f1stats.db.CachedStandings;
import com.f1stats.models.CircuitPitHistory;
import com.f1stats.models.CircuitStatsResponse;
import com.f1stats.models.TrackMap;
import com.f1stats.models.NewsResponse;
import com.f1stats.models.OnThisDayResponse;
import com.f1stats.models.RaceAnalysis;
import com.f1stats.models.RaceResult;
import com.f1stats.models.WeatherForecast;
import com.f1stats.notifications.ReminderScheduler;
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
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class F1Repository {

    public interface RepositoryCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }

    /**
     * A callback that may get {@code onSuccess} twice: the stored value at once, then the
     * refreshed one if it changed (stale-while-revalidate). Plain callbacks get one delivery.
     */
    public interface UpdatingCallback<T> extends RepositoryCallback<T> {}

    public interface SeasonStatsCallback {
        void onSuccess(Map<String, Integer> dnfs, Map<String, Integer> podiums);
    }

    private static final long ONE_HOUR_MS  = 60 * 60 * 1000L;
    private static final long ONE_DAY_MS   = 24 * ONE_HOUR_MS;
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>(){}.getType();

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
    // In-flight getSchedule requests keyed by year; guarded by itself
    private final Map<Integer, List<RepositoryCallback<List<Map<String, Object>>>>> inFlightSchedules = new HashMap<>();
    // In-flight stale-while-revalidate refreshes keyed by store key; guarded by itself
    private final Map<String, List<Waiter<?>>> inFlightRefreshes = new HashMap<>();

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

            // Home's season results and the widget often ask at once (both on a cold start)
            synchronized (inFlightSchedules) {
                List<RepositoryCallback<List<Map<String, Object>>>> waiting = inFlightSchedules.get(year);
                if (waiting != null) {
                    waiting.add(callback);
                    return;
                }
                List<RepositoryCallback<List<Map<String, Object>>>> first = new ArrayList<>();
                first.add(callback);
                inFlightSchedules.put(year, first);
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
                            deliverSchedule(year, races, snapshots, null);
                        } else if (!cached.isEmpty()) {
                            deliverSchedule(year, schedulesToMaps(cached), null, null);
                        } else {
                            deliverSchedule(year, null, null, "Could not load schedule");
                        }
                    }
                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (!cached.isEmpty()) {
                            deliverSchedule(year, schedulesToMaps(cached), null, null);
                        } else {
                            deliverSchedule(year, null, null, "Connection error: " + t.getMessage());
                        }
                    }
                })
            );
        });
    }

    /**
     * Main thread only. The first waiter gets {@code races}; any others get their own copy
     * (from {@code snapshots} when given), so no two callers share mutable maps.
     */
    private void deliverSchedule(int year, @Nullable List<Map<String, Object>> races,
                                 @Nullable List<String> snapshots, @Nullable String error) {
        List<RepositoryCallback<List<Map<String, Object>>>> waiting;
        synchronized (inFlightSchedules) {
            waiting = inFlightSchedules.remove(year);
        }
        if (waiting == null) return;
        for (int i = 0; i < waiting.size(); i++) {
            RepositoryCallback<List<Map<String, Object>>> cb = waiting.get(i);
            if (races == null) {
                cb.onError(error != null ? error : "Could not load schedule");
            } else if (i == 0) {
                cb.onSuccess(races);
            } else {
                List<String> json = snapshots;
                if (json == null) {
                    json = new ArrayList<>(races.size());
                    for (Map<String, Object> race : races) json.add(gson.toJson(race));
                }
                List<Map<String, Object>> copy = new ArrayList<>(json.size());
                for (String race : json) copy.add(gson.fromJson(race, MAP_TYPE));
                cb.onSuccess(copy);
            }
        }
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
        // Session times may have moved: reminders and result checks follow the new schedule
        if (year >= currentYear()) ReminderScheduler.rescheduleAsync(appContext);
    }

    private List<Map<String, Object>> schedulesToMaps(List<CachedSchedule> rows) {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        List<Map<String, Object>> out = new ArrayList<>();
        for (CachedSchedule row : rows) {
            out.add(gson.fromJson(row.sessionsJson, type));
        }
        return out;
    }

    /**
     * The next race, computed from the stored current-season schedule (first race whose Race
     * session hasn't finished; null in the offseason). Asks /schedule/next only when no
     * schedule is stored for the current season.
     */
    public void getNextRace(RepositoryCallback<Map<String, Object>> callback) {
        executor.execute(() -> {
            List<CachedSchedule> rows = db.scheduleDao().getByYear(currentYear());
            if (!rows.isEmpty()) {
                Map<String, Object> next = ScheduleClock.nextRace(schedulesToMaps(rows),
                        System.currentTimeMillis());
                mainHandler.post(() -> callback.onSuccess(next));
                return;
            }
            mainHandler.post(() -> api().getNextRace().enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call,
                                       Response<Map<String, Object>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        callback.onSuccess(response.body());
                    } else {
                        callback.onError("Could not load next race (HTTP " + response.code() + ")");
                    }
                }
                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    callback.onError("Connection error: " + t.getMessage());
                }
            }));
        });
    }

    /**
     * Whether a session in the stored current-season schedule started less than 4 hours ago
     * or starts within 30 minutes, i.e. whether /live is worth asking. False with no schedule.
     */
    public void isLiveWindowOpen(RepositoryCallback<Boolean> callback) {
        executor.execute(() -> {
            List<CachedSchedule> rows = db.scheduleDao().getByYear(currentYear());
            boolean open = !rows.isEmpty() && ScheduleClock.liveWindowOpen(schedulesToMaps(rows),
                    System.currentTimeMillis());
            mainHandler.post(() -> callback.onSuccess(open));
        });
    }

    // ── Results ───────────────────────────────────────────────────────────────

    private static final long LATEST_RESULTS_TTL_MS = 15 * 60 * 1000L;

    /**
     * Latest race results of a season (/results/latest), stale-while-revalidate: refreshed
     * after 15 minutes, or once the latest race has settled (start + 3h) if the stored copy
     * predates that. A past season's copy is permanent.
     */
    public void getLatestResults(int year, boolean forceRefresh,
                                 RepositoryCallback<Map<String, Object>> callback) {
        String key = "results-latest/Race/" + year;
        revalidate(key, jsonStore(key, MAP_TYPE), fetchedAt -> {
                    if (year < currentYear()) return StaleWhileRevalidate.FOREVER;
                    long settledAt = ScheduleClock.latestRaceSettledAt(
                            schedulesToMaps(db.scheduleDao().getByYear(year)), System.currentTimeMillis());
                    return ScheduleClock.ttlUntil(fetchedAt, LATEST_RESULTS_TTL_MS, settledAt);
                }, forceRefresh,
                () -> api().getLatestResults("Race", year), body -> body, "results", callback);
    }

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
        return hasResults(row.resultsJson) && isStoredRowFresh(row.fetchedAt, year, sessionTime, now);
    }

    /** The results freshness rule for any non-empty row derived from a session's results. */
    private boolean isStoredRowFresh(long fetchedAt, int year, @Nullable Long sessionTime, long now) {
        if (year < currentYear()) return true;
        boolean recent = sessionTime == null || now - (sessionTime + THREE_HOURS_MS) < RESULTS_SETTLE_MS;
        return !recent || now - fetchedAt < ONE_HOUR_MS;
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
        getDriverStandings(year, false, callback);
    }

    /** {@code forceRefresh} skips the Room row's freshness check (e.g. right after a race). */
    public void getDriverStandings(int year, boolean forceRefresh,
                                   RepositoryCallback<Map<String, Object>> callback) {
        getStandings(year, "driver", () -> api().getDriverStandings(year), forceRefresh, callback);
    }

    public void getConstructorStandings(int year, RepositoryCallback<Map<String, Object>> callback) {
        getConstructorStandings(year, false, callback);
    }

    public void getConstructorStandings(int year, boolean forceRefresh,
                                        RepositoryCallback<Map<String, Object>> callback) {
        getStandings(year, "constructor", () -> api().getConstructorStandings(year), forceRefresh, callback);
    }

    /**
     * Stale-while-revalidate over the cached_standings row: a past season's row is permanent,
     * the current season's is refetched after 1h (or when {@code forceRefresh}).
     */
    private void getStandings(int year, String type, Supplier<Call<Map<String, Object>>> request,
                               boolean forceRefresh, RepositoryCallback<Map<String, Object>> callback) {
        revalidate("standings/" + type + "/" + year, standingsStore(year, type),
                () -> seasonTtl(year, ONE_HOUR_MS), forceRefresh, request, body -> body,
                "standings", callback);
    }

    private Store<Map<String, Object>> standingsStore(int year, String type) {
        return new Store<Map<String, Object>>() {
            @Nullable
            @Override
            public Stored<Map<String, Object>> load() {
                CachedStandings row = db.standingsDao().get(year, type);
                Map<String, Object> data = row != null ? parseMap(row.standingsJson) : null;
                if (data == null) return null;
                // Restore season_started so ViewModel parsers see it
                data.put("season_started", row.seasonStarted);
                return new Stored<>(data, row.fetchedAt, row.standingsJson);
            }

            @Override
            public void save(String json, long fetchedAt) {
                Map<String, Object> body = parseMap(json);
                if (body == null) return;
                CachedStandings row = new CachedStandings();
                row.year          = year;
                row.type          = type;
                row.standingsJson = json;
                Object started    = body.get("season_started");
                row.seasonStarted = started instanceof Boolean && (Boolean) started;
                row.leaderGap     = 0;
                row.fetchedAt     = fetchedAt;
                db.standingsDao().upsert(row);
            }
        };
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

    /**
     * OpenF1 drivers for a season (2023+): headshots and team colours. Ids are synthetic.
     * Stored in cached_drivers: permanent for past seasons, refreshed daily for the current one.
     */
    public void fetchDrivers(int year, RepositoryCallback<List<CachedDriver>> callback) {
        revalidate("drivers/" + year, driversStore(year), () -> seasonTtl(year, ONE_DAY_MS), false,
                () -> api().getDriversByYear(year),
                // An empty list is an API hiccup or a season not started: keep what's stored
                raw -> {
                    List<CachedDriver> drivers = parseOpenF1Drivers(raw, year);
                    return drivers.isEmpty() ? null : drivers;
                },
                "drivers", callback);
    }

    private Store<List<CachedDriver>> driversStore(int year) {
        return new Store<List<CachedDriver>>() {
            @Nullable
            @Override
            public Stored<List<CachedDriver>> load() {
                List<CachedDriver> rows = db.driverDao().getBySeason(year);
                if (rows.isEmpty()) return null;
                long fetchedAt = Long.MAX_VALUE;
                for (CachedDriver row : rows) fetchedAt = Math.min(fetchedAt, row.fetchedAt);
                return new Stored<>(rows, fetchedAt, null);
            }

            @Override
            public void save(String json, long fetchedAt) {
                List<CachedDriver> drivers = gson.fromJson(json, new TypeToken<List<CachedDriver>>(){}.getType());
                if (drivers == null || drivers.isEmpty()) return;
                for (CachedDriver d : drivers) d.fetchedAt = fetchedAt;
                // Replace the season so a driver dropped from the grid doesn't linger
                db.runInTransaction(() -> {
                    db.driverDao().deleteBySeason(year);
                    db.driverDao().upsertAll(drivers);
                });
            }
        };
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

    // ── Track map ─────────────────────────────────────────────────────────────

    /**
     * Generated circuit map. A stored map is permanent (the data is static); a 404 is stored
     * as an empty row so the backend is only asked again after 7 days. Calls onError when
     * there is no map, so the caller falls back to its other renderings.
     */
    public void getTrackMap(String circuitId, RepositoryCallback<TrackMap> callback) {
        executor.execute(() -> {
            CachedTrackMap row = db.trackMapDao().get(circuitId);
            if (row != null) {
                if (row.json != null) {
                    TrackMap stored = gson.fromJson(row.json, TrackMap.class);
                    if (stored != null && stored.points.size() > 1) {
                        mainHandler.post(() -> callback.onSuccess(stored));
                        return;
                    }
                } else if (System.currentTimeMillis() - row.fetchedAt < SEVEN_DAYS_MS) {
                    mainHandler.post(() -> callback.onError("No track map for " + circuitId));
                    return;
                }
            }

            mainHandler.post(() ->
                api().getTrackMap(circuitId).enqueue(new retrofit2.Callback<TrackMap>() {
                    @Override
                    public void onResponse(retrofit2.Call<TrackMap> call, retrofit2.Response<TrackMap> response) {
                        TrackMap map = response.body();
                        boolean usable = response.isSuccessful() && map != null && map.points.size() > 1;
                        if (usable || response.code() == 404) executor.execute(() -> {
                            CachedTrackMap entity = new CachedTrackMap();
                            entity.circuitId = circuitId;
                            entity.json      = usable ? gson.toJson(map) : null;
                            entity.fetchedAt = System.currentTimeMillis();
                            db.trackMapDao().upsert(entity);
                        });
                        if (usable) {
                            callback.onSuccess(map);
                        } else {
                            callback.onError("No track map (HTTP " + response.code() + ")");
                        }
                    }
                    @Override
                    public void onFailure(retrofit2.Call<TrackMap> call, Throwable t) {
                        callback.onError("Connection error: " + t.getMessage());
                    }
                })
            );
        });
    }

    // ── Race analysis ─────────────────────────────────────────────────────────

    /**
     * Lap positions, lap times and pit stops for a race. Stored in Room under the same rules
     * as results: only once the race has ended, permanent for past seasons, refetched after
     * 1h while the race is under 72h old. A failed fetch serves the stored row if there is one.
     */
    public void getRaceAnalysis(int year, int round, RepositoryCallback<RaceAnalysis> callback) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            Long raceTime = getSessionTimeUtcMillis(year, round, "Race");
            CachedRaceAnalysis row = db.raceAnalysisDao().get(year, round);
            RaceAnalysis stored = parseRaceAnalysis(row);
            if (stored != null && isStoredRowFresh(row.fetchedAt, year, raceTime, now)) {
                mainHandler.post(() -> callback.onSuccess(stored));
                return;
            }
            mainHandler.post(() -> api().getRaceAnalysis(year, round).enqueue(new Callback<RaceAnalysis>() {
                @Override
                public void onResponse(Call<RaceAnalysis> call, Response<RaceAnalysis> response) {
                    RaceAnalysis body = response.body();
                    if (!response.isSuccessful() || body == null) {
                        serveStoredAnalysis(stored, "Could not load race analysis (HTTP "
                                + response.code() + ")", callback);
                        return;
                    }
                    if (body.drivers == null || body.drivers.isEmpty()) {
                        // Not published yet, or an API hiccup: prefer what we already have
                        callback.onSuccess(stored != null ? stored : body);
                        return;
                    }
                    String json = gson.toJson(body);
                    executor.execute(() -> {
                        long fetchedAt = System.currentTimeMillis();
                        if (!sessionEnded(year, raceTime, fetchedAt)) return;
                        CachedRaceAnalysis entity = new CachedRaceAnalysis();
                        entity.year      = year;
                        entity.round     = round;
                        entity.json      = json;
                        entity.fetchedAt = fetchedAt;
                        db.raceAnalysisDao().upsert(entity);
                    });
                    callback.onSuccess(body);
                }

                @Override
                public void onFailure(Call<RaceAnalysis> call, Throwable t) {
                    serveStoredAnalysis(stored, "Connection error: " + t.getMessage(), callback);
                }
            }));
        });
    }

    private static void serveStoredAnalysis(@Nullable RaceAnalysis stored, String error,
                                            RepositoryCallback<RaceAnalysis> callback) {
        DebugLog.d("F1Repository", error + (stored != null ? "; serving stored analysis" : ""));
        if (stored != null) callback.onSuccess(stored);
        else callback.onError(error);
    }

    /** The stored analysis, or null if there is no usable row. */
    @Nullable
    private RaceAnalysis parseRaceAnalysis(@Nullable CachedRaceAnalysis row) {
        if (row == null || row.json == null) return null;
        try {
            RaceAnalysis analysis = gson.fromJson(row.json, RaceAnalysis.class);
            return analysis != null && analysis.drivers != null && !analysis.drivers.isEmpty()
                    ? analysis : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Weather forecast, news, history, pit history (cached_json) ────────────

    /** The backend's default "this week" window: ±3 days around the date. */
    private static final int HISTORY_WINDOW_DAYS = 3;
    private static final long NEWS_TTL_MS = 15 * 60 * 1000L;
    private static final String HISTORY_KEY_PREFIX = "history/";

    /** Races at a circuit included in its pit strategy trend. */
    private static final int PIT_HISTORY_SEASONS = 10;

    /**
     * Pit stop trend for a circuit's recent races. Refreshed daily while the circuit is on the
     * current calendar (it may gain a race), otherwise permanent.
     */
    public void getCircuitPitHistory(String circuitId, RepositoryCallback<CircuitPitHistory> callback) {
        String key = "pit-history/" + circuitId;
        revalidate(key, jsonStore(key, CircuitPitHistory.class),
                () -> isOnCurrentCalendar(circuitId) ? ONE_DAY_MS : StaleWhileRevalidate.FOREVER, false,
                () -> api().getCircuitPitHistory(circuitId, PIT_HISTORY_SEASONS), body -> body,
                "pit history", callback);
    }

    /** Race weekend forecast; refreshed after 1 hour. */
    public void getWeatherForecast(int year, int round, boolean forceRefresh,
                                   RepositoryCallback<WeatherForecast> callback) {
        String key = "weather/" + year + "/" + round;
        revalidate(key, jsonStore(key, WeatherForecast.class), () -> ONE_HOUR_MS, forceRefresh,
                () -> api().getWeatherForecast(year, round), body -> body, "forecast", callback);
    }

    /** Latest headlines, newest first; refreshed after 15 minutes. */
    public void getNews(int limit, boolean forceRefresh, RepositoryCallback<NewsResponse> callback) {
        String key = "news/" + limit;
        revalidate(key, jsonStore(key, NewsResponse.class), () -> NEWS_TTL_MS, forceRefresh,
                () -> api().getNews(limit), body -> body, "news", callback);
    }

    /** Race winners from this week in past seasons; permanent per date, other dates dropped. */
    public void getOnThisDay(String dateIso, RepositoryCallback<OnThisDayResponse> callback) {
        String key = HISTORY_KEY_PREFIX + dateIso;
        executor.execute(() -> db.cachedJsonDao().deleteOthersWithPrefix(HISTORY_KEY_PREFIX, key));
        revalidate(key, jsonStore(key, OnThisDayResponse.class), () -> StaleWhileRevalidate.FOREVER, false,
                () -> api().getOnThisDay(dateIso, HISTORY_WINDOW_DAYS), body -> body, "history", callback);
    }

    /** Whether the current season's stored schedule visits the circuit; true if unknown. */
    @WorkerThread
    private boolean isOnCurrentCalendar(String circuitId) {
        List<CachedSchedule> rows = db.scheduleDao().getByYear(currentYear());
        if (rows.isEmpty()) return true;
        for (CachedSchedule row : rows) {
            Map<String, Object> race = parseMap(row.sessionsJson);
            if (race != null && circuitId.equals(race.get("circuit_id"))) return true;
        }
        return false;
    }

    // ── Stale-while-revalidate ────────────────────────────────────────────────

    /** A stored value; {@code json}, when known, detects a refresh that changed nothing. */
    private static final class Stored<T> {
        final T value;
        final long fetchedAt;
        @Nullable final String json;

        Stored(T value, long fetchedAt, @Nullable String json) {
            this.value = value;
            this.fetchedAt = fetchedAt;
            this.json = json;
        }
    }

    /** Where a value lives between launches. Both methods run on the executor. */
    private interface Store<T> {
        @WorkerThread @Nullable Stored<T> load();
        /** {@code json} is the fetched value, serialized on the main thread. */
        @WorkerThread void save(String json, long fetchedAt);
    }

    /** A caller waiting on a refresh, with what it was (or wasn't) already served. */
    private static final class Waiter<T> {
        final RepositoryCallback<T> callback;
        @Nullable final Stored<T> stored;
        final boolean storedServed;

        Waiter(RepositoryCallback<T> callback, @Nullable Stored<T> stored, boolean storedServed) {
            this.callback = callback;
            this.stored = stored;
            this.storedServed = storedServed;
        }
    }

    /** A {@link CachedJson} row holding a value of {@code type}. */
    private <T> Store<T> jsonStore(String key, Type type) {
        return new Store<T>() {
            @Nullable
            @Override
            public Stored<T> load() {
                CachedJson row = db.cachedJsonDao().get(key);
                if (row == null || row.json == null) return null;
                try {
                    T value = gson.fromJson(row.json, type);
                    return value != null ? new Stored<>(value, row.fetchedAt, row.json) : null;
                } catch (RuntimeException e) {
                    return null;   // unreadable (model changed): treat as missing
                }
            }

            @Override
            public void save(String json, long fetchedAt) {
                CachedJson row = new CachedJson();
                row.key       = key;
                row.json      = json;
                row.fetchedAt = fetchedAt;
                db.cachedJsonDao().put(row);
            }
        };
    }

    /** {@code currentTtl} for this season (or a future one), permanent for past seasons. */
    private long seasonTtl(int year, long currentTtl) {
        return year < currentYear() ? StaleWhileRevalidate.FOREVER : currentTtl;
    }

    /**
     * Serves the stored value at once if there is one (even stale), then fetches when it is
     * older than {@code ttlMs} (evaluated on the executor) or {@code forceRefresh}. A fetched
     * value is stored before delivery; {@link UpdatingCallback}s that already got the stored
     * value get it too if it changed. A failed fetch keeps the stored value and only reports
     * an error when nothing was stored. Concurrent refreshes of one key share a request.
     *
     * @param fromResponse runs on the main thread; null means the response is unusable and
     *                     counts as a failure (the stored value is kept)
     */
    private <R, T> void revalidate(String key, Store<T> store, LongSupplier ttlMs, boolean forceRefresh,
                                   Supplier<Call<R>> request, Function<R, T> fromResponse,
                                   String what, RepositoryCallback<T> callback) {
        revalidate(key, store, fetchedAt -> ttlMs.getAsLong(), forceRefresh, request, fromResponse,
                what, callback);
    }

    /** As above, with a TTL that depends on when the stored value was fetched. */
    private <R, T> void revalidate(String key, Store<T> store, LongUnaryOperator ttlForFetchedAt,
                                   boolean forceRefresh, Supplier<Call<R>> request,
                                   Function<R, T> fromResponse, String what,
                                   RepositoryCallback<T> callback) {
        executor.execute(() -> {
            Stored<T> stored = store.load();
            StaleWhileRevalidate.Plan plan = StaleWhileRevalidate.plan(stored != null,
                    stored != null ? stored.fetchedAt : 0,
                    stored != null ? ttlForFetchedAt.applyAsLong(stored.fetchedAt) : 0,
                    System.currentTimeMillis(), forceRefresh);
            if (plan != StaleWhileRevalidate.Plan.FETCH) {
                T value = stored.value;
                mainHandler.post(() -> callback.onSuccess(value));
                if (plan == StaleWhileRevalidate.Plan.SERVE_STORED) return;
            }

            Waiter<T> waiter = new Waiter<>(callback, stored, plan != StaleWhileRevalidate.Plan.FETCH);
            synchronized (inFlightRefreshes) {
                List<Waiter<?>> waiting = inFlightRefreshes.get(key);
                if (waiting != null) {
                    waiting.add(waiter);
                    return;
                }
                List<Waiter<?>> first = new ArrayList<>();
                first.add(waiter);
                inFlightRefreshes.put(key, first);
            }

            mainHandler.post(() -> request.get().enqueue(new Callback<R>() {
                @Override
                public void onResponse(Call<R> call, Response<R> response) {
                    R body = response.body();
                    T value = null;
                    String json = null;
                    if (response.isSuccessful() && body != null) {
                        try {
                            value = fromResponse.apply(body);
                            // Snapshot on the main thread — Gson's LinkedTreeMap is not thread-safe
                            if (value != null) json = gson.toJson(value);
                        } catch (RuntimeException e) {
                            DebugLog.d("F1Repository", key + ": unreadable response: " + e);
                            value = null;
                        }
                    }
                    if (value == null) {
                        completeRefresh(key, null, null, "Could not load " + what
                                + " (HTTP " + response.code() + ")");
                        return;
                    }
                    T fetched = value;
                    String snapshot = json;
                    executor.execute(() -> {
                        store.save(snapshot, System.currentTimeMillis());
                        mainHandler.post(() -> completeRefresh(key, fetched, snapshot, null));
                    });
                }

                @Override
                public void onFailure(Call<R> call, Throwable t) {
                    completeRefresh(key, null, null, "Connection error: " + t.getMessage());
                }
            }));
        });
    }

    /** Main thread only. Delivers the fetched value, or on failure each waiter's fallback. */
    @SuppressWarnings("unchecked")
    private <T> void completeRefresh(String key, @Nullable T value, @Nullable String json,
                                     @Nullable String error) {
        List<Waiter<?>> waiting;
        synchronized (inFlightRefreshes) {
            waiting = inFlightRefreshes.remove(key);
        }
        if (waiting == null) return;
        for (Waiter<?> w : waiting) {
            Waiter<T> waiter = (Waiter<T>) w;
            if (value != null) {
                boolean unchanged = waiter.stored != null && json != null && json.equals(waiter.stored.json);
                if (StaleWhileRevalidate.deliverFetched(waiter.storedServed,
                        waiter.callback instanceof UpdatingCallback, unchanged)) {
                    waiter.callback.onSuccess(value);
                }
                continue;
            }
            switch (StaleWhileRevalidate.onFailure(waiter.stored != null, waiter.storedServed)) {
                case SERVE_STORED:
                    DebugLog.d("F1Repository", error + "; serving stored value");
                    waiter.callback.onSuccess(waiter.stored.value);
                    break;
                case REPORT_ERROR:
                    waiter.callback.onError(error != null ? error : "Could not load data");
                    break;
                case KEEP_SERVED:
                    DebugLog.d("F1Repository", error + "; keeping stored value");
                    break;
            }
        }
    }

    @Nullable
    private Map<String, Object> parseMap(@Nullable String json) {
        if (json == null) return null;
        try {
            return gson.fromJson(json, MAP_TYPE);
        } catch (RuntimeException e) {
            return null;
        }
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
