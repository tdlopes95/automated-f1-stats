package com.f1stats.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.format.DateFormat;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.TaskStackBuilder;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.f1stats.CustomizeHomeActivity;
import com.f1stats.MainActivity;
import com.f1stats.R;
import com.f1stats.SeasonHelper;
import com.f1stats.data.F1Repository;
import com.f1stats.db.CachedDriver;
import com.f1stats.home.HomeCardParams;
import com.f1stats.home.HomeCardType;
import com.f1stats.home.HomeLayoutStore;
import com.f1stats.models.DriverStanding;
import com.f1stats.notifications.ReminderPlanner;
import com.f1stats.util.DebugLog;
import com.f1stats.util.HeadToHead;
import com.f1stats.util.TeamColors;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Rebuilds the {@link WidgetSnapshot} through {@link F1Repository} (Room first, so usually
 * no network) and renders every {@link FavouriteDriverWidget} from it. Refreshes keep the
 * previous data for whatever fails to load. Nothing here runs on the main thread.
 */
public final class FavouriteWidgetUpdater {

    private FavouriteWidgetUpdater() {}

    private static final String TAG = "FavouriteWidget";

    // Generous: the backend may be cold-starting
    private static final long LOAD_TIMEOUT_S = 90;
    // Short: rendering may run inside a broadcast receiver's goAsync() window
    private static final long HEADSHOT_TIMEOUT_S = 4;
    private static final int MAX_UPCOMING = 12;
    /** A snapshot older than this is refreshed when the widget updates. */
    static final long STALE_AFTER_MS = 6 * 60 * 60 * 1000L;

    private static final int REQUEST_HOME = 2001;
    private static final int REQUEST_CHOOSE = 2002;

    // Refreshes can wait on the network; renders must stay quick, so they don't share a queue
    private static final Executor REFRESH_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Executor RENDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Gson GSON = new Gson();

    // ── Entry points ──────────────────────────────────────────────────────────

    public static void refreshAsync(@NonNull Context context) {
        refreshAsync(context, null, null);
    }

    /** With data Home already loaded; anything null is loaded through the repository. */
    public static void refreshAsync(@NonNull Context context,
                                    @Nullable List<DriverStanding> standings,
                                    @Nullable HeadToHead.Season season) {
        Context app = context.getApplicationContext();
        REFRESH_EXECUTOR.execute(() -> refresh(app, standings, season));
    }

    /** Blocking; for workers. */
    @WorkerThread
    public static void refresh(@NonNull Context context) {
        refresh(context.getApplicationContext(), null, null);
    }

    /** Blocking, with driver standings the caller just fetched (null: load them as usual). */
    @WorkerThread
    public static void refresh(@NonNull Context context, @Nullable List<DriverStanding> standings) {
        refresh(context.getApplicationContext(), standings, null);
    }

    /** The "standings" list of a driver standings response; null if it has none. */
    @Nullable
    public static List<DriverStanding> parseDriverStandings(@NonNull Map<String, Object> body) {
        if (!(body.get("standings") instanceof List)) return null;
        return GSON.fromJson(GSON.toJson(body.get("standings")),
                new TypeToken<List<DriverStanding>>() {}.getType());
    }

    /** Renders from the stored snapshot; {@code onDone} runs on the background thread. */
    public static void renderAsync(@NonNull Context context, @NonNull Runnable onDone) {
        Context app = context.getApplicationContext();
        RENDER_EXECUTOR.execute(() -> {
            try {
                renderAll(app);
            } catch (RuntimeException e) {
                DebugLog.d(TAG, "render failed: " + e);
            } finally {
                onDone.run();
            }
        });
    }

    /** True when the stored snapshot is missing, for another driver or old. */
    public static boolean isStale(@NonNull Context context) {
        String favouriteId = HomeLayoutStore.getInstance(context).getFavouriteDriverId();
        if (favouriteId == null) return false;
        WidgetSnapshot snapshot = WidgetSnapshot.load(context);
        return snapshot == null || !favouriteId.equals(snapshot.driverId)
                || System.currentTimeMillis() - snapshot.updatedAt > STALE_AFTER_MS;
    }

    static int[] widgetIds(@NonNull Context context) {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(
                new ComponentName(context, FavouriteDriverWidget.class));
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @WorkerThread
    private static void refresh(Context app, @Nullable List<DriverStanding> standings,
                                @Nullable HeadToHead.Season season) {
        try {
            if (widgetIds(app).length == 0) return;
            HomeLayoutStore store = HomeLayoutStore.getInstance(app);
            String driverId = store.getFavouriteDriverId();
            WidgetSnapshot old = WidgetSnapshot.load(app);
            boolean sameDriver = old != null && driverId != null && driverId.equals(old.driverId);
            // A new favourite shows its name straight away, before its data arrives
            if (driverId != null && !sameDriver) renderAll(app);

            int year = SeasonHelper.getCurrentYear();
            long now = System.currentTimeMillis();
            List<WidgetSnapshot.Session> upcoming = loadUpcoming(app, year, now);
            if (upcoming == null) upcoming = old != null ? old.upcoming : new ArrayList<>();

            WidgetSnapshot snapshot;
            if (driverId == null) {
                snapshot = new WidgetSnapshot();
            } else {
                if (standings == null) standings = loadStandings(app, year);
                if (standings == null) {
                    // Offline: keep what we had for this driver
                    snapshot = sameDriver ? old : newSnapshot(driverId, store);
                } else {
                    if (season == null) season = loadSeason(app, year);
                    snapshot = newSnapshot(driverId, store);
                    fillStanding(snapshot, driverId, standings);
                    if (season != null) fillLastRace(snapshot, driverId, season);
                    else if (sameDriver) copyLastRace(old, snapshot);
                    fillOpenF1(app, snapshot, year, sameDriver ? old : null);
                }
            }
            snapshot.upcoming = upcoming;
            snapshot.updatedAt = now;
            snapshot.save(app);
            renderAll(app);
        } catch (RuntimeException e) {
            DebugLog.d(TAG, "refresh failed: " + e);
        }
    }

    private static WidgetSnapshot newSnapshot(String driverId, HomeLayoutStore store) {
        WidgetSnapshot s = new WidgetSnapshot();
        s.driverId = driverId;
        s.name = store.getConfig(HomeCardType.FAVOURITE_DRIVER).getParam(HomeCardParams.DRIVER_NAME);
        return s;
    }

    private static void fillStanding(WidgetSnapshot s, String driverId, List<DriverStanding> standings) {
        for (int i = 0; i < standings.size(); i++) {
            DriverStanding d = standings.get(i);
            if (d.getDriver() == null || !driverId.equals(d.getDriver().getDriverId())) continue;
            s.inStandings = true;
            s.name = d.getDriver().getFullName();
            s.code = d.getDriver().getCode();
            s.constructorId = d.getConstructorId();
            s.teamName = d.getTeamName();
            s.position = parsePosition(d.getPosition(), i);
            s.points = parseNumber(d.getPoints());
            s.gapToAhead = i > 0 ? parseNumber(standings.get(i - 1).getPoints()) - s.points : 0;
            return;
        }
    }

    private static void fillLastRace(WidgetSnapshot s, String driverId, HeadToHead.Season season) {
        List<HeadToHead.FormEntry> last =
                HeadToHead.forDriver(season, HeadToHead.DriverRef.of(driverId), 1).lastResults;
        if (last.isEmpty()) return;
        HeadToHead.FormEntry entry = last.get(0);
        s.lastRaceName = entry.raceName != null ? entry.raceName : "";
        s.lastRacePosition = entry.position;
        s.lastRaceStatus = entry.status;
    }

    private static void copyLastRace(WidgetSnapshot from, WidgetSnapshot to) {
        to.lastRaceName = from.lastRaceName;
        to.lastRacePosition = from.lastRacePosition;
        to.lastRaceStatus = from.lastRaceStatus;
    }

    /** Headshot and team colour from OpenF1 (2023 on), matched by code then number. */
    private static void fillOpenF1(Context app, WidgetSnapshot s, int year, @Nullable WidgetSnapshot old) {
        if (year < 2023 || s.code == null) return;
        List<CachedDriver> drivers = FavouriteWidgetUpdater.<List<CachedDriver>, List<CachedDriver>>await(cb -> F1Repository.getInstance(app).fetchDrivers(year, cb),
                list -> list);
        if (drivers == null) {
            if (old != null) {
                s.headshotUrl = old.headshotUrl;
                s.teamColour = old.teamColour;
            }
            return;
        }
        for (CachedDriver d : drivers) {
            if (s.code.equalsIgnoreCase(d.code)) {
                s.headshotUrl = d.headshotUrl;
                s.teamColour = d.teamColour;
                return;
            }
        }
    }

    // ── Loading (blocks this worker thread on the repository's main-thread callbacks) ──

    private interface Request<T> {
        void start(F1Repository.RepositoryCallback<T> callback);
    }

    /** {@code convert} runs on the main thread, before anything else can touch the data. */
    @Nullable
    private static <T, R> R await(Request<T> request, Function<T, R> convert) {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<R> out = new AtomicReference<>();
        request.start(new F1Repository.RepositoryCallback<T>() {
            @Override
            public void onSuccess(T data) {
                try {
                    if (data != null) out.set(convert.apply(data));
                } catch (RuntimeException e) {
                    DebugLog.d(TAG, "parse error: " + e);
                } finally {
                    done.countDown();
                }
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
        return out.get();
    }

    @Nullable
    private static List<DriverStanding> loadStandings(Context app, int year) {
        return FavouriteWidgetUpdater.<Map<String, Object>, List<DriverStanding>>await(
                cb -> F1Repository.getInstance(app).getDriverStandings(year, cb),
                FavouriteWidgetUpdater::parseDriverStandings);
    }

    @Nullable
    private static HeadToHead.Season loadSeason(Context app, int year) {
        return FavouriteWidgetUpdater.<HeadToHead.Season, HeadToHead.Season>await(
                cb -> F1Repository.getInstance(app).getSeasonResults(year, cb), s -> s);
    }

    /** Sessions after {@code now}, soonest first; null if the schedule didn't load. */
    @Nullable
    private static List<WidgetSnapshot.Session> loadUpcoming(Context app, int year, long now) {
        List<String> races = FavouriteWidgetUpdater.<List<Map<String, Object>>, List<String>>await(
                cb -> F1Repository.getInstance(app).getSchedule(year, cb), list -> {
            List<String> json = new ArrayList<>();
            for (Map<String, Object> race : list) json.add(GSON.toJson(race));
            return json;
        });
        if (races == null) return null;
        List<ReminderPlanner.Session> sessions = new ArrayList<>();
        for (String json : races) {
            Map<String, Object> race = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
            int round = race != null ? (int) parseNumber(String.valueOf(race.get("round"))) : 0;
            for (ReminderPlanner.Session s : ReminderPlanner.parseRound(year, round, null, json)) {
                if (s.startMillis > now) sessions.add(s);
            }
        }
        sessions.sort((a, b) -> Long.compare(a.startMillis, b.startMillis));
        List<WidgetSnapshot.Session> out = new ArrayList<>();
        for (ReminderPlanner.Session s : sessions) {
            if (out.size() == MAX_UPCOMING) break;
            out.add(new WidgetSnapshot.Session(s.name, s.raceName, s.startMillis));
        }
        return out;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /** Synchronised so a render never lands after one that read a newer snapshot. */
    @WorkerThread
    static synchronized void renderAll(@NonNull Context app) {
        int[] ids = widgetIds(app);
        if (ids.length == 0) return;

        HomeLayoutStore store = HomeLayoutStore.getInstance(app);
        String favouriteId = store.getFavouriteDriverId();
        String favouriteName = store.getConfig(HomeCardType.FAVOURITE_DRIVER)
                .getParam(HomeCardParams.DRIVER_NAME);
        WidgetSnapshot snapshot = WidgetSnapshot.load(app);
        WidgetText.Lines lines = WidgetText.build(WidgetText.Templates.from(app), snapshot,
                favouriteId, favouriteName, System.currentTimeMillis(),
                DateFormat.is24HourFormat(app), TimeZone.getDefault(), Locale.getDefault());

        boolean current = snapshot != null && favouriteId != null && favouriteId.equals(snapshot.driverId);
        int colour = current
                ? TeamColors.get(app, snapshot.constructorId, snapshot.teamName, snapshot.teamColour)
                : TeamColors.get(app, null, null, null);
        Bitmap headshot = current ? loadHeadshot(app, snapshot.headshotUrl) : null;
        PendingIntent click = lines.chooseDriver ? chooseDriverIntent(app) : homeIntent(app);

        RemoteViews full = bind(app, R.layout.widget_favourite_driver, true, lines, colour, headshot, click);
        RemoteViews views;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews compact = bind(app, R.layout.widget_favourite_driver_compact, false,
                    lines, colour, null, click);
            Map<SizeF, RemoteViews> sizes = new HashMap<>();
            sizes.put(new SizeF(110f, 40f), compact);
            sizes.put(new SizeF(180f, 100f), full);
            views = new RemoteViews(sizes);
        } else {
            views = full;
        }
        AppWidgetManager.getInstance(app).updateAppWidget(ids, views);
    }

    private static RemoteViews bind(Context app, int layout, boolean full, WidgetText.Lines lines,
                                    int colour, @Nullable Bitmap headshot, PendingIntent click) {
        RemoteViews v = new RemoteViews(app.getPackageName(), layout);
        v.setOnClickPendingIntent(R.id.widget_root, click);
        v.setViewVisibility(R.id.widget_prompt, lines.chooseDriver ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.widget_content, lines.chooseDriver ? View.GONE : View.VISIBLE);
        if (lines.chooseDriver) return v;

        v.setInt(R.id.widget_team_strip, "setColorFilter", colour);
        v.setTextViewText(R.id.widget_driver_name, lines.name);
        v.setTextViewText(R.id.widget_position_points, lines.positionPoints);
        if (!full) return v;

        v.setTextViewText(R.id.widget_gap, lines.gap);
        v.setViewVisibility(R.id.widget_gap, lines.gap.isEmpty() ? View.GONE : View.VISIBLE);
        v.setTextViewText(R.id.widget_last_race, lines.lastRace);
        v.setViewVisibility(R.id.widget_last_race, lines.lastRace.isEmpty() ? View.GONE : View.VISIBLE);
        v.setTextViewText(R.id.widget_next_session, lines.nextSession);
        if (headshot != null) v.setImageViewBitmap(R.id.widget_headshot, headshot);
        else v.setImageViewResource(R.id.widget_headshot, 0);
        return v;
    }

    /**
     * Loaded synchronously: a size-mapped RemoteViews can't be changed once built, so the
     * bitmap has to be in each layout before it is. Glide's disk cache keeps it offline.
     */
    @Nullable
    private static Bitmap loadHeadshot(Context app, @Nullable String url) {
        if (url == null || url.isEmpty()) return null;
        int size = app.getResources().getDimensionPixelSize(R.dimen.headshot_size_md);
        try {
            return Glide.with(app).asBitmap().load(url)
                    .apply(RequestOptions.circleCropTransform())
                    .submit(size, size)
                    .get(HEADSHOT_TIMEOUT_S, TimeUnit.SECONDS);
        } catch (Exception e) {
            DebugLog.d(TAG, "headshot failed: " + e);
            return null;
        }
    }

    private static PendingIntent homeIntent(Context app) {
        Intent intent = new Intent(app, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // Own request code: NextSessionWidget's MainActivity intent differs only in extras
        return PendingIntent.getActivity(app, REQUEST_HOME, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent chooseDriverIntent(Context app) {
        Intent intent = new Intent(app, CustomizeHomeActivity.class)
                .putExtra(CustomizeHomeActivity.EXTRA_OPEN_OPTIONS, HomeCardType.FAVOURITE_DRIVER.name());
        return TaskStackBuilder.create(app)
                .addNextIntentWithParentStack(intent)
                .getPendingIntent(REQUEST_CHOOSE,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private static int parsePosition(@Nullable String position, int index) {
        try {
            return position != null ? Integer.parseInt(position.trim()) : index + 1;
        } catch (NumberFormatException e) {
            return index + 1;
        }
    }

    private static double parseNumber(@Nullable String value) {
        try {
            return value != null ? Double.parseDouble(value.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
