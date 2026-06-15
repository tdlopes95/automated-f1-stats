package com.f1stats.db;

import androidx.room.Entity;

@Entity(tableName = "cached_results", primaryKeys = {"year", "round", "sessionType"})
public class CachedResult {
    public int year;
    public int round;
    public String sessionType;
    public String resultsJson;
    public long fetchedAt;
}
