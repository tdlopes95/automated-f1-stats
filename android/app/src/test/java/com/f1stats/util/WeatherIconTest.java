package com.f1stats.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WeatherIconTest {

    private static void assertCodes(WeatherIcon expected, int... codes) {
        for (int code : codes) assertEquals("WMO " + code, expected, WeatherIcon.forWmoCode(code));
    }

    @Test
    public void clearAndClouds() {
        assertCodes(WeatherIcon.CLEAR, 0);
        assertCodes(WeatherIcon.PARTLY_CLOUDY, 1, 2);
        assertCodes(WeatherIcon.CLOUDY, 3);
        assertCodes(WeatherIcon.FOG, 45, 48);
    }

    @Test
    public void precipitation() {
        assertCodes(WeatherIcon.RAIN, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67);
        assertCodes(WeatherIcon.SHOWERS, 80, 81, 82);
        assertCodes(WeatherIcon.SNOW, 71, 73, 75, 77, 85, 86);
        assertCodes(WeatherIcon.THUNDERSTORM, 95, 96, 99);
    }

    @Test
    public void missingCodeHasNoIcon_unknownCodeFallsBackToCloudy() {
        assertNull(WeatherIcon.forWmoCode(null));
        assertCodes(WeatherIcon.CLOUDY, 4, 50, 70, 90, 100, -1);
    }
}
