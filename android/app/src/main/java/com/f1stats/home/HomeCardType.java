package com.f1stats.home;

import androidx.annotation.StringRes;

import com.f1stats.R;

/**
 * Every card Home can show. Declaration order is the default layout order.
 * Cards that are not {@link #isAvailable() available} are filtered out of the stored layout,
 * Home and the Customize screen (a way to reserve a type before its card exists).
 */
public enum HomeCardType {

    NEXT_RACE(true, true, false, R.string.home_card_next_race_name, R.string.home_card_next_race_desc),
    CHAMPIONSHIP_BATTLE(true, true, false, R.string.home_card_championship_name, R.string.home_card_championship_desc),
    LAST_WINNER(true, true, false, R.string.home_card_last_winner_name, R.string.home_card_last_winner_desc),

    FAVOURITE_DRIVER(true, false, true, R.string.home_card_favourite_driver_name, R.string.home_card_favourite_driver_desc),
    FAVOURITE_TEAM(true, false, true, R.string.home_card_favourite_team_name, R.string.home_card_favourite_team_desc),
    PINNED_H2H(true, false, true, R.string.home_card_pinned_h2h_name, R.string.home_card_pinned_h2h_desc),
    CHAMPIONSHIP_SNAPSHOT(true, false, true, R.string.home_card_snapshot_name, R.string.home_card_snapshot_desc),

    WEEKEND_WEATHER(true, false, false, R.string.home_card_weekend_weather_name, R.string.home_card_weekend_weather_desc),
    NEWS(true, false, true, R.string.home_card_news_name, R.string.home_card_news_desc),
    ON_THIS_DAY(true, false, false, R.string.home_card_on_this_day_name, R.string.home_card_on_this_day_desc);

    private final boolean available;
    private final boolean defaultEnabled;
    private final boolean hasOptions;
    @StringRes private final int nameRes;
    @StringRes private final int descriptionRes;

    HomeCardType(boolean available, boolean defaultEnabled, boolean hasOptions,
                 @StringRes int nameRes, @StringRes int descriptionRes) {
        this.available = available;
        this.defaultEnabled = defaultEnabled;
        this.hasOptions = hasOptions;
        this.nameRes = nameRes;
        this.descriptionRes = descriptionRes;
    }

    public boolean isAvailable() { return available; }

    /** Whether the card is shown in the default layout. */
    public boolean isDefaultEnabled() { return defaultEnabled; }

    /** True if the card has per-card options (the gear on its Customize row). */
    public boolean hasOptions() { return hasOptions; }

    @StringRes public int getNameRes() { return nameRes; }
    @StringRes public int getDescriptionRes() { return descriptionRes; }

    /** Looks a stored name up; null for names this build doesn't know. */
    public static HomeCardType fromName(String name) {
        if (name == null) return null;
        for (HomeCardType type : values()) {
            if (type.name().equals(name)) return type;
        }
        return null;
    }
}
