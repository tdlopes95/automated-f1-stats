package com.f1stats.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A response stored as JSON under a key such as "news/10" or "weather/2026/18". */
@Entity(tableName = "cached_json")
public class CachedJson {
    @PrimaryKey @NonNull public String key = "";
    public String json;
    public long fetchedAt;
}
