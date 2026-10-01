package com.f1stats.home;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persists the ordered Home card list as JSON. Loading is forgiving: unknown or reserved types
 * are dropped, duplicates keep their first position, and available types missing from the
 * stored list are appended disabled, so new card types show up in Customize automatically.
 */
public class HomeLayoutStore {

    /** Bump when the JSON shape changes incompatibly. */
    static final int SCHEMA_VERSION = 1;

    private static final String PREFS_NAME = "home_layout";
    private static final String KEY_LAYOUT = "layout";

    /** Raw key-value access, so the store can be tested on the JVM. */
    public interface Storage {
        @Nullable String read();
        void write(@NonNull String json);
    }

    public interface Listener {
        void onLayoutChanged(@NonNull List<HomeCardConfig> layout);
    }

    private static HomeLayoutStore instance;

    private final Storage storage;
    private final Gson gson = new Gson();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private List<HomeCardConfig> layout;
    private boolean welcomeDismissed;

    public HomeLayoutStore(@NonNull Storage storage) {
        this.storage = storage;
    }

    public static synchronized HomeLayoutStore getInstance(Context context) {
        if (instance == null) {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            instance = new HomeLayoutStore(new Storage() {
                @Override public String read() { return prefs.getString(KEY_LAYOUT, null); }
                @Override public void write(@NonNull String json) {
                    prefs.edit().putString(KEY_LAYOUT, json).apply();
                }
            });
        }
        return instance;
    }

    /** Every available card type in its stored order, enabled or not. Returns copies. */
    @NonNull
    public synchronized List<HomeCardConfig> getLayout() {
        ensureLoaded();
        return copyOf(layout);
    }

    /** A copy of one card's config (enabled or not). */
    @NonNull
    public HomeCardConfig getConfig(@NonNull HomeCardType type) {
        for (HomeCardConfig config : getLayout()) {
            if (config.getType() == type) return config;
        }
        return new HomeCardConfig(type, false);
    }

    /** The favourite driver's Jolpica driverId, set even while its card is hidden. */
    @Nullable
    public String getFavouriteDriverId() {
        return getConfig(HomeCardType.FAVOURITE_DRIVER).getParam(HomeCardParams.DRIVER_ID);
    }

    /** The favourite team's Jolpica constructorId, set even while its card is hidden. */
    @Nullable
    public String getFavouriteConstructorId() {
        return getConfig(HomeCardType.FAVOURITE_TEAM).getParam(HomeCardParams.CONSTRUCTOR_ID);
    }

    /** Replaces one card's config, keeping its position. */
    public void updateConfig(@NonNull HomeCardConfig updated) {
        List<HomeCardConfig> list = getLayout();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getType() == updated.getType()) list.set(i, updated.copy());
        }
        save(list);
    }

    public synchronized boolean isWelcomeDismissed() {
        ensureLoaded();
        return welcomeDismissed;
    }

    /** Remembers that the "Make Home yours" prompt was dismissed. */
    public void setWelcomeDismissed(boolean dismissed) {
        List<HomeCardConfig> snapshot;
        synchronized (this) {
            ensureLoaded();
            welcomeDismissed = dismissed;
            storage.write(toJson(layout));
            snapshot = copyOf(layout);
        }
        notifyListeners(snapshot);
    }

    private void ensureLoaded() {
        if (layout == null) layout = parse(storage.read());
    }

    /** The cards Home should show, in order. */
    @NonNull
    public List<HomeCardType> getEnabledTypes() {
        List<HomeCardType> types = new ArrayList<>();
        for (HomeCardConfig config : getLayout()) {
            if (config.isEnabled()) types.add(config.getType());
        }
        return types;
    }

    public void save(@NonNull List<HomeCardConfig> newLayout) {
        List<HomeCardConfig> normalised;
        synchronized (this) {
            ensureLoaded();   // keeps welcomeDismissed
            normalised = normalise(newLayout);
            layout = normalised;
            storage.write(toJson(normalised));
        }
        notifyListeners(copyOf(normalised));
    }

    /** Default visibility and order; each card keeps its options (favourites etc.). */
    public void resetToDefault() {
        List<HomeCardConfig> current = getLayout();
        List<HomeCardConfig> reset = new ArrayList<>();
        for (HomeCardConfig config : defaultLayout()) {
            Map<String, String> params = null;
            for (HomeCardConfig c : current) {
                if (c.getType() == config.getType()) params = c.getParams();
            }
            reset.add(new HomeCardConfig(config.getType(), config.isEnabled(), params));
        }
        save(reset);
    }

    public void addListener(@NonNull Listener listener) { listeners.add(listener); }
    public void removeListener(@NonNull Listener listener) { listeners.remove(listener); }

    /** All available card types in declaration order, enabled per their default. */
    @NonNull
    public static List<HomeCardConfig> defaultLayout() {
        List<HomeCardConfig> list = new ArrayList<>();
        for (HomeCardType type : HomeCardType.values()) {
            if (type.isAvailable()) list.add(new HomeCardConfig(type, type.isDefaultEnabled()));
        }
        return list;
    }

    // ── JSON ──────────────────────────────────────────────────────────────────

    private static class StoredLayout {
        int schemaVersion;
        List<StoredCard> cards;
        boolean welcomeDismissed;
    }

    private static class StoredCard {
        String type;
        boolean enabled;
        Map<String, String> params;
    }

    @NonNull
    String toJson(@NonNull List<HomeCardConfig> list) {
        StoredLayout stored = new StoredLayout();
        stored.schemaVersion = SCHEMA_VERSION;
        stored.welcomeDismissed = welcomeDismissed;
        stored.cards = new ArrayList<>();
        for (HomeCardConfig config : list) {
            StoredCard card = new StoredCard();
            card.type = config.getType().name();
            card.enabled = config.isEnabled();
            card.params = config.getParams();
            stored.cards.add(card);
        }
        return gson.toJson(stored);
    }

    /** Parses the stored layout; also restores {@link #welcomeDismissed}. */
    @NonNull
    List<HomeCardConfig> parse(@Nullable String json) {
        welcomeDismissed = false;
        if (json == null || json.isEmpty()) return defaultLayout();
        StoredLayout stored;
        try {
            stored = gson.fromJson(json, StoredLayout.class);
        } catch (JsonParseException | IllegalStateException e) {
            return defaultLayout();
        }
        // A newer, incompatible schema (e.g. after a downgrade) isn't worth guessing at
        if (stored == null || stored.cards == null || stored.schemaVersion > SCHEMA_VERSION) {
            return defaultLayout();
        }
        welcomeDismissed = stored.welcomeDismissed;
        List<HomeCardConfig> list = new ArrayList<>();
        for (StoredCard card : stored.cards) {
            if (card == null) continue;
            HomeCardType type = HomeCardType.fromName(card.type);
            if (type == null) continue;
            list.add(new HomeCardConfig(type, card.enabled, card.params));
        }
        return normalise(list);
    }

    /** Drops unavailable types and duplicates, appends missing available types disabled. */
    @NonNull
    private static List<HomeCardConfig> normalise(@NonNull List<HomeCardConfig> input) {
        List<HomeCardConfig> out = new ArrayList<>();
        Set<HomeCardType> seen = EnumSet.noneOf(HomeCardType.class);
        for (HomeCardConfig config : input) {
            if (config == null || !config.getType().isAvailable()) continue;
            if (seen.add(config.getType())) out.add(config.copy());
        }
        for (HomeCardType type : HomeCardType.values()) {
            if (type.isAvailable() && !seen.contains(type)) {
                out.add(new HomeCardConfig(type, false));
            }
        }
        return out;
    }

    private static List<HomeCardConfig> copyOf(List<HomeCardConfig> list) {
        List<HomeCardConfig> copy = new ArrayList<>(list.size());
        for (HomeCardConfig config : list) copy.add(config.copy());
        return copy;
    }

    private void notifyListeners(List<HomeCardConfig> snapshot) {
        for (Listener listener : listeners) listener.onLayoutChanged(snapshot);
    }
}
