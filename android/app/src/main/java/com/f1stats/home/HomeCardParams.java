package com.f1stats.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Param keys for {@link HomeCardConfig#getParams()}. Favourites are stored by Jolpica id, with a
 * display name alongside so Customize can label the choice offline.
 */
public final class HomeCardParams {

    private HomeCardParams() {}

    // FAVOURITE_DRIVER
    public static final String DRIVER_ID   = "driverId";
    public static final String DRIVER_NAME = "driverName";

    // FAVOURITE_TEAM
    public static final String CONSTRUCTOR_ID = "constructorId";
    public static final String TEAM_NAME      = "teamName";

    // PINNED_H2H
    public static final String DRIVER_ID_1   = "driverId1";
    public static final String DRIVER_NAME_1 = "driverName1";
    public static final String DRIVER_ID_2   = "driverId2";
    public static final String DRIVER_NAME_2 = "driverName2";
    /** "true": pair the favourite driver with their current teammate. */
    public static final String TEAMMATES_OF_FAVOURITE = "teammatesOfFavourite";

    // CHAMPIONSHIP_SNAPSHOT
    public static final String MODE              = "mode";
    public static final String MODE_DRIVERS      = "drivers";
    public static final String MODE_CONSTRUCTORS = "constructors";

    // NEWS
    /** Comma-separated source names to show; absent means every source. */
    public static final String NEWS_SOURCES = "sources";

    /** True if the card can't show anything until the user picks something in its options. */
    public static boolean needsChoice(@NonNull HomeCardConfig config) {
        switch (config.getType()) {
            case FAVOURITE_DRIVER:
                return config.getParam(DRIVER_ID) == null;
            case FAVOURITE_TEAM:
                return config.getParam(CONSTRUCTOR_ID) == null;
            case PINNED_H2H:
                return !config.getBooleanParam(TEAMMATES_OF_FAVOURITE)
                        && (config.getParam(DRIVER_ID_1) == null || config.getParam(DRIVER_ID_2) == null);
            default:
                return false;
        }
    }

    public static boolean isConstructorsMode(@NonNull HomeCardConfig config) {
        return MODE_CONSTRUCTORS.equals(config.getParam(MODE));
    }

    /** The NEWS card's chosen sources, in stored order; empty means all sources. */
    @NonNull
    public static List<String> newsSources(@NonNull HomeCardConfig config) {
        return splitSources(config.getParam(NEWS_SOURCES));
    }

    @NonNull
    static List<String> splitSources(@Nullable String value) {
        List<String> sources = new ArrayList<>();
        if (value == null) return sources;
        for (String part : value.split(",")) {
            String source = part.trim();
            if (!source.isEmpty() && !sources.contains(source)) sources.add(source);
        }
        return sources;
    }

    /**
     * The NEWS_SOURCES value for a selection: null (all sources) when nothing or every
     * available source is selected, so sources added later aren't filtered out.
     */
    @Nullable
    public static String joinSources(@NonNull Collection<String> selected,
                                     @NonNull Collection<String> available) {
        if (selected.isEmpty() || selected.containsAll(available)) return null;
        return String.join(",", selected);
    }
}
