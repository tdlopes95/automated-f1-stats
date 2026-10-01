package com.f1stats.util;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.f1stats.R;

/** A handful of weather icons covering the WMO weather codes Open-Meteo returns. */
public enum WeatherIcon {

    CLEAR(R.drawable.ic_weather_clear),
    PARTLY_CLOUDY(R.drawable.ic_weather_partly_cloudy),
    CLOUDY(R.drawable.ic_weather_cloudy),
    FOG(R.drawable.ic_weather_fog),
    RAIN(R.drawable.ic_weather_rain),
    SHOWERS(R.drawable.ic_weather_showers),
    THUNDERSTORM(R.drawable.ic_weather_thunderstorm),
    SNOW(R.drawable.ic_weather_snow);

    @DrawableRes private final int drawable;

    WeatherIcon(@DrawableRes int drawable) {
        this.drawable = drawable;
    }

    @DrawableRes
    public int getDrawable() {
        return drawable;
    }

    /**
     * Icon for a WMO weather code (https://open-meteo.com/en/docs, "WMO Weather interpretation
     * codes"); null when there is no code. Unknown codes fall back to {@link #CLOUDY}.
     */
    @Nullable
    public static WeatherIcon forWmoCode(@Nullable Integer code) {
        if (code == null) return null;
        int c = code;
        if (c == 0) return CLEAR;
        if (c == 1 || c == 2) return PARTLY_CLOUDY;           // mainly clear, partly cloudy
        if (c == 3) return CLOUDY;                            // overcast
        if (c == 45 || c == 48) return FOG;                   // fog, rime fog
        if (c >= 51 && c <= 67) return RAIN;                  // drizzle, rain, freezing rain
        if (c >= 71 && c <= 77) return SNOW;                  // snow fall, snow grains
        if (c >= 80 && c <= 82) return SHOWERS;               // rain showers
        if (c == 85 || c == 86) return SNOW;                  // snow showers
        if (c >= 95 && c <= 99) return THUNDERSTORM;          // thunderstorm, with hail
        return CLOUDY;
    }
}
