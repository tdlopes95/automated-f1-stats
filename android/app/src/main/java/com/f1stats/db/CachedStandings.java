package com.f1stats.db;

import androidx.room.Entity;

@Entity(tableName = "cached_standings", primaryKeys = {"year", "type"})
public class CachedStandings {
    public int year;
    public String type;
    public String standingsJson;
    public boolean seasonStarted;
    public double leaderGap;
    public long fetchedAt;
}
