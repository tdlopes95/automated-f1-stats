package com.f1stats.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory cache for data that isn't worth a Room table (forecasts, headlines, history).
 * Entries are kept past their TTL so a failed refresh can fall back to the last good value.
 */
final class MemoryCache<V> {

    private static final class Entry<V> {
        final V value;
        final long storedAt;

        Entry(V value, long storedAt) {
            this.value = value;
            this.storedAt = storedAt;
        }
    }

    private final long ttlMs;
    private final Map<String, Entry<V>> entries = new HashMap<>();

    /** @param ttlMs how long a value counts as fresh; {@code Long.MAX_VALUE} for "until replaced". */
    MemoryCache(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    /** The value for {@code key} if stored less than the TTL ago, else null. */
    @Nullable
    synchronized V getFresh(@NonNull String key, long now) {
        Entry<V> entry = entries.get(key);
        return entry != null && now - entry.storedAt < ttlMs ? entry.value : null;
    }

    /** The last value stored for {@code key}, however old; null if none. */
    @Nullable
    synchronized V getLast(@NonNull String key) {
        Entry<V> entry = entries.get(key);
        return entry != null ? entry.value : null;
    }

    synchronized void put(@NonNull String key, @NonNull V value, long now) {
        entries.put(key, new Entry<>(value, now));
    }

    /** Keeps only {@code key}'s entry (for caches where any other key is out of date). */
    synchronized void retainOnly(@NonNull String key) {
        entries.keySet().retainAll(java.util.Collections.singleton(key));
    }
}
