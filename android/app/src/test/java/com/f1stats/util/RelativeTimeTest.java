package com.f1stats.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RelativeTimeTest {

    private static final long NOW = 1_790_000_000_000L;
    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private static void assertAge(long ageMs, RelativeTime.Unit unit, long value) {
        RelativeTime t = RelativeTime.between(NOW - ageMs, NOW);
        assertEquals("unit for age " + ageMs, unit, t.unit);
        assertEquals("value for age " + ageMs, value, t.value);
    }

    @Test
    public void underAMinuteIsNow() {
        assertAge(0, RelativeTime.Unit.NOW, 0);
        assertAge(59 * SECOND, RelativeTime.Unit.NOW, 0);
    }

    @Test
    public void futureTimesAreNow() {
        assertAge(-5 * MINUTE, RelativeTime.Unit.NOW, 0);
    }

    @Test
    public void minutesHoursDaysRoundDown() {
        assertAge(MINUTE, RelativeTime.Unit.MINUTES, 1);
        assertAge(59 * MINUTE + 59 * SECOND, RelativeTime.Unit.MINUTES, 59);
        assertAge(HOUR, RelativeTime.Unit.HOURS, 1);
        assertAge(23 * HOUR + 59 * MINUTE, RelativeTime.Unit.HOURS, 23);
        assertAge(DAY, RelativeTime.Unit.DAYS, 1);
        assertAge(6 * DAY + 23 * HOUR, RelativeTime.Unit.DAYS, 6);
    }

    @Test
    public void aWeekOrOlderIsADate() {
        assertAge(7 * DAY, RelativeTime.Unit.DATE, 0);
        assertAge(400 * DAY, RelativeTime.Unit.DATE, 0);
    }
}
