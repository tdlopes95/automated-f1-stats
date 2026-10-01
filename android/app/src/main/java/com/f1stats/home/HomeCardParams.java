package com.f1stats.home;

import androidx.annotation.NonNull;

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
}
