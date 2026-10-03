package com.f1stats.data;

import static com.f1stats.data.StaleWhileRevalidate.FOREVER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.f1stats.data.StaleWhileRevalidate.OnFailure;
import com.f1stats.data.StaleWhileRevalidate.Plan;

import org.junit.Test;

public class StaleWhileRevalidateTest {

    private static final long HOUR = 60 * 60 * 1000L;
    private static final long NOW = 1_000 * HOUR;

    @Test
    public void fresh_servesStoredWithoutRequest() {
        assertEquals(Plan.SERVE_STORED,
                StaleWhileRevalidate.plan(true, NOW - HOUR + 1, HOUR, NOW, false));
    }

    @Test
    public void stale_servesStoredThenFetches() {
        assertEquals(Plan.SERVE_STORED_THEN_FETCH,
                StaleWhileRevalidate.plan(true, NOW - HOUR, HOUR, NOW, false));
        assertEquals(Plan.SERVE_STORED_THEN_FETCH,
                StaleWhileRevalidate.plan(true, NOW - 30 * HOUR, HOUR, NOW, false));
    }

    @Test
    public void missing_fetches() {
        assertEquals(Plan.FETCH, StaleWhileRevalidate.plan(false, 0, HOUR, NOW, false));
        assertEquals(Plan.FETCH, StaleWhileRevalidate.plan(false, 0, FOREVER, NOW, false));
    }

    @Test
    public void forceRefresh_fetchesEvenWhenFresh() {
        assertEquals(Plan.FETCH, StaleWhileRevalidate.plan(true, NOW, HOUR, NOW, true));
    }

    @Test
    public void forever_neverStale() {
        assertEquals(Plan.SERVE_STORED, StaleWhileRevalidate.plan(true, 0, FOREVER, NOW, false));
    }

    @Test
    public void futureTimestamp_countsAsStale() {
        assertFalse(StaleWhileRevalidate.isFresh(NOW + HOUR, HOUR, NOW));
        assertEquals(Plan.SERVE_STORED_THEN_FETCH,
                StaleWhileRevalidate.plan(true, NOW + 1, HOUR, NOW, false));
    }

    @Test
    public void error_withStoredAlreadyServed_keepsIt() {
        assertEquals(OnFailure.KEEP_SERVED, StaleWhileRevalidate.onFailure(true, true));
    }

    @Test
    public void error_withStoredNotServed_servesIt() {
        // Forced refresh that failed: fall back to the stored value
        assertEquals(OnFailure.SERVE_STORED, StaleWhileRevalidate.onFailure(true, false));
    }

    @Test
    public void error_withoutStored_reportsError() {
        assertEquals(OnFailure.REPORT_ERROR, StaleWhileRevalidate.onFailure(false, false));
    }

    @Test
    public void fetched_deliveredWhenNothingServedYet() {
        assertTrue(StaleWhileRevalidate.deliverFetched(false, false, false));
        assertTrue(StaleWhileRevalidate.deliverFetched(false, true, true));
    }

    @Test
    public void fetched_afterStored_onlyToUpdatingCallersAndOnlyIfChanged() {
        assertTrue(StaleWhileRevalidate.deliverFetched(true, true, false));
        assertFalse(StaleWhileRevalidate.deliverFetched(true, true, true));
        // A single-shot caller (e.g. a worker awaiting one result) keeps the stored value
        assertFalse(StaleWhileRevalidate.deliverFetched(true, false, false));
    }
}
