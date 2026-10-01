package com.f1stats.util;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;

import org.junit.Test;

public class HeadToHeadTest {

    private static final Gson GSON = new Gson();
    private static final HeadToHead.DriverRef RUS = HeadToHead.DriverRef.of("russell");
    private static final HeadToHead.DriverRef ANT = HeadToHead.DriverRef.of("antonelli");

    /** One result entry as the backend stores it. */
    private static String result(String driverId, String position, String points,
                                 String grid, String status) {
        return "{\"position\":\"" + position + "\",\"points\":\"" + points + "\",\"grid\":\""
                + grid + "\",\"status\":\"" + status + "\",\"Driver\":{\"driverId\":\""
                + driverId + "\",\"code\":\"" + driverId.substring(0, 3).toUpperCase() + "\"}}";
    }

    private static String body(String raceName, String... results) {
        return "{\"race_name\":\"" + raceName + "\",\"results\":[" + String.join(",", results) + "]}";
    }

    private static void add(HeadToHead.Season season, int round, String session, String json) {
        season.addResultsJson(round, session, json, GSON);
    }

    @Test
    public void points_includeSprints() {
        HeadToHead.Season season = new HeadToHead.Season(2026);
        add(season, 1, "Race", body("Australian GP",
                result("russell", "1", "25", "1", "Finished"),
                result("antonelli", "2", "18", "3", "Finished")));
        add(season, 2, "Sprint", body("Chinese GP",
                result("antonelli", "1", "8", "1", "Finished"),
                result("russell", "3", "6", "2", "Finished")));
        add(season, 2, "Race", body("Chinese GP",
                result("antonelli", "1", "25", "2", "Finished"),
                result("russell", "4", "12", "3", "Finished")));

        HeadToHead.Comparison c = HeadToHead.compare(season, RUS, ANT);
        assertEquals(43, c.driver1.points, 0.001);
        assertEquals(51, c.driver2.points, 0.001);
        // Sprint wins and podiums don't count
        assertEquals(1, c.driver1.wins);
        assertEquals(1, c.driver2.wins);
        assertEquals(1, c.driver1.podiums);
        assertEquals(2, c.driver2.podiums);
        // Sprint grid slots aren't poles
        assertEquals(1, c.driver1.poles);
        assertEquals(0, c.driver2.poles);

        HeadToHead.DriverSeason single = HeadToHead.forDriver(season, ANT, 5);
        assertEquals(51, single.stats.points, 0.001);
    }

    @Test
    public void lapped_countsAsFinished() {
        HeadToHead.Season season = new HeadToHead.Season(2026);
        add(season, 1, "Race", body("GP",
                result("russell", "12", "0", "10", "Lapped"),
                result("antonelli", "14", "0", "12", "+1 Lap")));
        add(season, 2, "Race", body("GP",
                result("russell", "6", "8", "5", "Finished"),
                result("antonelli", "18", "0", "6", "Engine")));

        HeadToHead.Comparison c = HeadToHead.compare(season, RUS, ANT);
        assertEquals(2, c.driver1.finishCount);
        assertEquals(9.0, c.driver1.avgFinish(), 0.001);
        assertEquals(0, c.driver1.dnfs);
        // Retirement: not in the average, counted as a DNF
        assertEquals(1, c.driver2.finishCount);
        assertEquals(14.0, c.driver2.avgFinish(), 0.001);
        assertEquals(1, c.driver2.dnfs);
    }

    @Test
    public void dns_isExcludedFromTally() {
        HeadToHead.Season season = new HeadToHead.Season(2026);
        add(season, 1, "Race", body("GP",
                result("russell", "5", "10", "4", "Finished"),
                result("antonelli", "20", "0", "0", "Did not start")));
        add(season, 2, "Race", body("GP",
                result("russell", "3", "15", "2", "Finished"),
                result("antonelli", "2", "18", "1", "Finished")));

        HeadToHead.Comparison c = HeadToHead.compare(season, RUS, ANT);
        assertEquals(0, c.driver1.h2hWins);
        assertEquals(1, c.driver2.h2hWins);
        // A non-start is neither a DNF nor a finish
        assertEquals(0, c.driver2.dnfs);
        assertEquals(1, c.driver2.finishCount);
    }

    @Test
    public void retiredDriver_losesRoundToFinisher() {
        HeadToHead.Season season = new HeadToHead.Season(2026);
        // The finisher was lapped and still classified ahead of the retirement
        add(season, 1, "Race", body("GP",
                result("antonelli", "16", "0", "2", "Lapped"),
                result("russell", "19", "0", "1", "Collision")));

        HeadToHead.Comparison c = HeadToHead.compare(season, RUS, ANT);
        assertEquals(0, c.driver1.h2hWins);
        assertEquals(1, c.driver2.h2hWins);
        assertEquals(1, c.driver1.dnfs);
    }

    @Test
    public void forDriver_lastResultsAreMostRecentOldestFirst() {
        HeadToHead.Season season = new HeadToHead.Season(2026);
        String[] statuses = {"Finished", "Finished", "Gearbox", "Finished", "Did not start",
                "Finished", "Lapped"};
        // Added out of order on purpose: rounds are kept sorted
        for (int round = statuses.length; round >= 1; round--) {
            add(season, round, "Race", body("GP " + round,
                    result("russell", String.valueOf(round), "1", "3", statuses[round - 1])));
        }

        HeadToHead.DriverSeason ds = HeadToHead.forDriver(season, RUS, 5);
        assertEquals(5, ds.lastResults.size());
        assertEquals(3, ds.lastResults.get(0).round);
        assertEquals("GP 3", ds.lastResults.get(0).raceName);
        assertEquals(true, ds.lastResults.get(0).isDnf());
        assertEquals(true, ds.lastResults.get(2).didNotStart());
        assertEquals(7, ds.lastResults.get(4).position);
        assertEquals(1, ds.stats.dnfs);
        assertEquals(1, ds.stats.wins);
    }

    @Test
    public void codeFallback_whenNoDriverId() {
        HeadToHead.Season season = new HeadToHead.Season(2026);
        add(season, 1, "Race", body("GP", result("russell", "1", "25", "1", "Finished")));
        HeadToHead.DriverSeason ds = HeadToHead.forDriver(season,
                new HeadToHead.DriverRef(null, "rus"), 5);
        assertEquals(25, ds.stats.points, 0.001);
    }

    @Test
    public void otherSessionsAndBadJson_areIgnored() {
        HeadToHead.Season season = new HeadToHead.Season(2026);
        add(season, 1, "Qualifying", body("GP", result("russell", "1", "0", "0", "")));
        add(season, 2, "Race", "{broken");
        add(season, 3, "Race", null);
        assertEquals(0, season.getRounds().size());
    }
}
