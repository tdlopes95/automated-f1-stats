package com.f1stats.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MemoryCacheTest {

    @Test
    public void freshWithinTtl_lastGoodKeptAfterExpiry() {
        MemoryCache<String> cache = new MemoryCache<>(1000);
        cache.put("k", "v", 10_000);
        assertEquals("v", cache.getFresh("k", 10_999));
        assertNull(cache.getFresh("k", 11_000));
        assertEquals("v", cache.getLast("k"));
        assertNull(cache.getLast("other"));
    }

    @Test
    public void retainOnly_dropsOtherKeys() {
        MemoryCache<String> cache = new MemoryCache<>(Long.MAX_VALUE);
        cache.put("2026-10-01", "yesterday", 0);
        cache.put("2026-10-02", "today", 0);
        cache.retainOnly("2026-10-02");
        assertNull(cache.getLast("2026-10-01"));
        assertEquals("today", cache.getFresh("2026-10-02", Long.MAX_VALUE - 1));
    }
}
