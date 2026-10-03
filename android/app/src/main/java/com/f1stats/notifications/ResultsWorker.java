package com.f1stats.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.f1stats.SeasonHelper;
import com.f1stats.data.F1Repository;
import com.f1stats.home.HomeLayoutStore;
import com.f1stats.models.DriverStanding;
import com.f1stats.models.RaceResult;
import com.f1stats.notifications.ReminderPlanner.Session;
import com.f1stats.notifications.ReminderPlanner.SessionKind;
import com.f1stats.util.DebugLog;
import com.f1stats.widget.FavouriteWidgetUpdater;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Loads a Race or Sprint result through {@link F1Repository} and notifies the favourite driver
 * and team's finish. Retries with exponential backoff while results are empty, until 8 hours
 * after the session start. Each session is notified at most once.
 */
public class ResultsWorker extends Worker {

    static final String TAG = "results_check";
    private static final String DEBUG_WORK_NAME = "results_check_debug";

    private static final String KEY_YEAR = "year";
    private static final String KEY_ROUND = "round";
    private static final String KEY_SESSION = "session";
    private static final String KEY_RACE_NAME = "race_name";
    private static final String KEY_START = "start";
    private static final String KEY_CIRCUIT = "circuit";
    private static final String KEY_CIRCUIT_ID = "circuit_id";
    private static final String KEY_HAS_SPRINT = "has_sprint";
    private static final String KEY_FORCE = "force";

    // Generous: the backend may be cold-starting
    private static final long LOAD_TIMEOUT_S = 90;

    public ResultsWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Unique per session; KEEP so a reschedule doesn't reset a running backoff. */
    static void enqueue(@NonNull WorkManager wm, @NonNull Session session, long delayMillis) {
        wm.enqueueUniqueWork("results_" + session.key(), ExistingWorkPolicy.KEEP,
                request(session, delayMillis, false));
    }

    /** Debug: check a session now, even if it was already notified. */
    public static void runNow(@NonNull Context context, @NonNull Session session) {
        WorkManager.getInstance(context).enqueueUniqueWork(DEBUG_WORK_NAME,
                ExistingWorkPolicy.REPLACE, request(session, 0, true));
    }

    private static OneTimeWorkRequest request(Session s, long delayMillis, boolean force) {
        Data input = new Data.Builder()
                .putInt(KEY_YEAR, s.year)
                .putInt(KEY_ROUND, s.round)
                .putString(KEY_SESSION, s.name)
                .putString(KEY_RACE_NAME, s.raceName)
                .putLong(KEY_START, s.startMillis)
                .putString(KEY_CIRCUIT, s.circuit)
                .putString(KEY_CIRCUIT_ID, s.circuitId)
                .putBoolean(KEY_HAS_SPRINT, s.hasSprint)
                .putBoolean(KEY_FORCE, force)
                .build();
        return new OneTimeWorkRequest.Builder(ResultsWorker.class)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .setInputData(input)
                .addTag(TAG)
                .build();
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        Session session = sessionFromInput(getInputData());
        if (session == null) return Result.failure();
        boolean force = getInputData().getBoolean(KEY_FORCE, false);
        NotificationSettings settings = NotificationSettings.getInstance(context);
        String key = session.key();

        if (!force && (!settings.isResultsEnabled() || settings.getNotifiedKeys().contains(key))) {
            return Result.success();
        }
        HomeLayoutStore store = HomeLayoutStore.getInstance(context);
        String driverId = store.getFavouriteDriverId();
        String constructorId = store.getFavouriteConstructorId();
        if (driverId == null && constructorId == null) return Result.success();

        List<RaceResult> results = loadResults(context, session);
        long now = System.currentTimeMillis();
        if (results.isEmpty()) {
            if (!force && !ReminderPlanner.shouldGiveUp(session.startMillis, now)) {
                DebugLog.d(TAG, key + ": no results yet, retry " + getRunAttemptCount());
                return Result.retry();
            }
            DebugLog.d(TAG, key + ": giving up");
            settings.markNotified(key, SeasonHelper.getCurrentYear());
            return Result.success();
        }

        List<String> lines = ResultMessages.lines(ResultMessages.Templates.from(context), results,
                driverId, constructorId, session.raceName, session.kind == SessionKind.SPRINT);
        if (!lines.isEmpty()) Notifications.showResults(context, session, lines);
        // Also when the favourites didn't take part, so the catch-up rule doesn't re-run it
        settings.markNotified(key, SeasonHelper.getCurrentYear());
        DebugLog.d(TAG, key + ": notified " + lines.size() + " line(s)");
        refreshStandingsAndWidget(context);
        return Result.success();
    }

    @NonNull
    private static List<RaceResult> loadResults(Context context, Session session) {
        return parseResults(await(cb -> F1Repository.getInstance(context)
                .getResults(session.year, session.round, session.name, cb)));
    }

    /**
     * New results are in: refetch this season's standings past the repository's cache, then
     * update the widget with the driver standings. If the backend still has pre-race standings,
     * the widget's 6-hourly refresh catches up.
     */
    private static void refreshStandingsAndWidget(Context context) {
        int year = SeasonHelper.getCurrentYear();
        F1Repository repo = F1Repository.getInstance(context);
        Map<String, Object> drivers = await(cb -> repo.getDriverStandings(year, true, cb));
        await(cb -> repo.getConstructorStandings(year, true, cb));
        List<DriverStanding> standings = null;
        try {
            if (drivers != null) standings = FavouriteWidgetUpdater.parseDriverStandings(drivers);
        } catch (RuntimeException e) {
            DebugLog.d(TAG, "standings parse error: " + e);
        }
        FavouriteWidgetUpdater.refresh(context, standings);
    }

    /** Blocks this worker thread on the repository's main-thread callback; null on error. */
    @Nullable
    private static Map<String, Object> await(
            Consumer<F1Repository.RepositoryCallback<Map<String, Object>>> request) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Map<String, Object>> body = new AtomicReference<>();
        request.accept(new F1Repository.RepositoryCallback<Map<String, Object>>() {
            @Override
            public void onSuccess(Map<String, Object> data) {
                body.set(data);
                done.countDown();
            }
            @Override
            public void onError(String error) {
                DebugLog.d(TAG, "load error: " + error);
                done.countDown();
            }
        });
        try {
            if (!done.await(LOAD_TIMEOUT_S, TimeUnit.SECONDS)) return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return body.get();
    }

    @NonNull
    private static List<RaceResult> parseResults(@Nullable Map<String, Object> body) {
        if (body == null || !(body.get("results") instanceof List)) return new ArrayList<>();
        Gson gson = new Gson();
        try {
            List<RaceResult> parsed = gson.fromJson(gson.toJson(body.get("results")),
                    new TypeToken<List<RaceResult>>() {}.getType());
            return parsed != null ? parsed : new ArrayList<>();
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    @Nullable
    private static Session sessionFromInput(Data in) {
        String name = in.getString(KEY_SESSION);
        SessionKind kind = SessionKind.fromName(name);
        if (name == null || kind == null) return null;
        String raceName = in.getString(KEY_RACE_NAME);
        return new Session(in.getInt(KEY_YEAR, 0), in.getInt(KEY_ROUND, 0),
                raceName != null ? raceName : "", name, kind, in.getLong(KEY_START, 0),
                in.getString(KEY_CIRCUIT), in.getString(KEY_CIRCUIT_ID),
                in.getBoolean(KEY_HAS_SPRINT, false));
    }
}
