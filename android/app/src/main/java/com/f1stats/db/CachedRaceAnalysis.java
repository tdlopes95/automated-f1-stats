package com.f1stats.db;

import androidx.room.Entity;

@Entity(tableName = "cached_race_analysis", primaryKeys = {"year", "round"})
public class CachedRaceAnalysis {
    public int year;
    public int round;
    public String json;
    public long fetchedAt;
}
