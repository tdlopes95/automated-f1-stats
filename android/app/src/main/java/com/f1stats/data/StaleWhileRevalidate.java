package com.f1stats.data;

/**
 * The decisions behind the repository's stale-while-revalidate reads, kept free of Android so
 * they can be unit tested. A stored value is served at once, even when stale; a stale (or
 * missing) one is then refreshed from the network, and a failed refresh keeps what's stored.
 */
public final class StaleWhileRevalidate {

    private StaleWhileRevalidate() {}

    /** TTL for values that never go stale (past seasons, per-date history). */
    public static final long FOREVER = Long.MAX_VALUE;

    public enum Plan {
        /** Nothing usable stored (or a forced refresh): fetch, the stored value is only a fallback. */
        FETCH,
        /** Fresh: serve the stored value, no request. */
        SERVE_STORED,
        /** Stale: serve the stored value now, then refresh it. */
        SERVE_STORED_THEN_FETCH
    }

    public enum OnFailure {
        /** The stored value was already served: keep it, report nothing. */
        KEEP_SERVED,
        /** Not served yet, but there is one: serve it instead of the error. */
        SERVE_STORED,
        /** Nothing stored: report the error. */
        REPORT_ERROR
    }

    /**
     * Whether a value stored at {@code fetchedAt} is still within {@code ttlMs} at {@code now}.
     * A timestamp in the future (the clock moved back) counts as stale.
     */
    public static boolean isFresh(long fetchedAt, long ttlMs, long now) {
        if (ttlMs == FOREVER) return true;
        long age = now - fetchedAt;
        return age >= 0 && age < ttlMs;
    }

    public static Plan plan(boolean hasStored, long fetchedAt, long ttlMs, long now, boolean forceRefresh) {
        if (!hasStored || forceRefresh) return Plan.FETCH;
        return isFresh(fetchedAt, ttlMs, now) ? Plan.SERVE_STORED : Plan.SERVE_STORED_THEN_FETCH;
    }

    /**
     * Whether a caller gets the fetched value. One that already got the stored value only gets
     * the fetched one if it accepts a second delivery and the value actually changed.
     */
    public static boolean deliverFetched(boolean storedServed, boolean acceptsUpdates, boolean unchanged) {
        return !storedServed || (acceptsUpdates && !unchanged);
    }

    public static OnFailure onFailure(boolean hasStored, boolean storedServed) {
        if (storedServed) return OnFailure.KEEP_SERVED;
        return hasStored ? OnFailure.SERVE_STORED : OnFailure.REPORT_ERROR;
    }
}
