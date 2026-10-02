package com.f1stats.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;

/** A generated circuit map. A null json marks a 404 ("no map"), rechecked after 7 days. */
@Entity(tableName = "cached_track_map", primaryKeys = {"circuitId"})
public class CachedTrackMap {
    @NonNull
    public String circuitId = "";
    public String json;
    public long fetchedAt;
}
