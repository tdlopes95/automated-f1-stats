package com.f1stats.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class RequestStatsTest {

    @After
    public void tearDown() {
        RequestStats.clear();
    }

    @Test
    public void normalize_replacesYearsRoundsKeysAndCircuitIds() {
        assertEquals("/results/{y}/{r}", RequestStats.normalize("/results/2026/18"));
        assertEquals("/drivers/{y}", RequestStats.normalize("/drivers/2025"));
        assertEquals("/sessions/{n}/stints", RequestStats.normalize("/sessions/9839/stints"));
        assertEquals("/circuit/{id}/pit-history", RequestStats.normalize("/circuit/monza/pit-history"));
        assertEquals("/track-map/{id}", RequestStats.normalize("/track-map/red_bull_ring"));
        assertEquals("/standings/drivers", RequestStats.normalize("/standings/drivers"));
        assertEquals("/", RequestStats.normalize("/"));
    }

    @Test
    public void summary_countsDuplicateUrls() {
        RequestStats.record("/results/2026/1", "https://x/results/2026/1");
        RequestStats.record("/results/2026/2", "https://x/results/2026/2");
        RequestStats.record("/drivers/2026", "https://x/drivers/2026");
        RequestStats.record("/drivers/2026", "https://x/drivers/2026");

        String summary = RequestStats.summary();
        assertTrue(summary, summary.startsWith("total=4 uniqueUrls=3 duplicates=1"));
        assertTrue(summary, summary.contains("2  /drivers/{y}"));
        assertTrue(summary, summary.contains("2  /results/{y}/{r}"));
        assertTrue(summary, summary.contains("2x https://x/drivers/2026"));
    }
}
