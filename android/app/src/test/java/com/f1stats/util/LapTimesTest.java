package com.f1stats.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LapTimesTest {

    @Test
    public void formatsMinutesSecondsAndTenths() {
        assertEquals("1:23.5", LapTimes.formatLapTime(83_456));
        assertEquals("1:23.4", LapTimes.formatLapTime(83_449));
        assertEquals("1:05.0", LapTimes.formatLapTime(65_000));
        assertEquals("0:59.9", LapTimes.formatLapTime(59_940));
        assertEquals("0:00.0", LapTimes.formatLapTime(0));
    }

    @Test
    public void roundingCarriesIntoTheNextMinute() {
        assertEquals("1:00.0", LapTimes.formatLapTime(59_960));
        assertEquals("2:00.0", LapTimes.formatLapTime(119_999));
    }

    @Test
    public void negativeLapTimeIsBlank() {
        assertEquals("", LapTimes.formatLapTime(-1));
    }

    @Test
    public void formatsShortDurationsAsSeconds() {
        assertEquals("22.3", LapTimes.formatSeconds(22_345));
        assertEquals("2.0", LapTimes.formatSeconds(1_960));
        assertEquals("1:02.3", LapTimes.formatSeconds(62_345));
    }

    @Test
    public void medianIgnoresNulls() {
        assertEquals(90_000.0, LapTimes.median(Arrays.asList(null, 80_000L, 90_000L, 100_000L)), 0.0);
        assertEquals(85_000.0, LapTimes.median(Arrays.asList(80_000L, 90_000L)), 0.0);
        assertNull(LapTimes.median(Arrays.asList(null, null)));
        assertNull(LapTimes.median(Collections.emptyList()));
    }

    @Test
    public void slowLapsAreReplacedByNullKeepingIndices() {
        List<Long> laps = Arrays.asList(120_000L, 90_000L, 91_000L, null, 115_000L, 90_500L);
        // Median of the non-null laps is 91_000; the limit is 104_650
        assertEquals(Arrays.asList(null, 90_000L, 91_000L, null, null, 90_500L),
                LapTimes.withoutSlowLaps(laps));
    }

    @Test
    public void lapExactlyAtTheLimitIsKept() {
        List<Long> laps = Arrays.asList(100_000L, 100_000L, 115_000L);
        assertEquals(laps, LapTimes.withoutSlowLaps(laps));
    }

    @Test
    public void filterDoesNotModifyTheInput() {
        List<Long> laps = Arrays.asList(90_000L, 90_000L, 200_000L);
        LapTimes.withoutSlowLaps(laps);
        assertEquals(Long.valueOf(200_000L), laps.get(2));
    }

    @Test
    public void allNullLapsAreReturnedUnchanged() {
        List<Long> laps = Arrays.asList(null, null);
        assertEquals(laps, LapTimes.withoutSlowLaps(laps));
    }
}
