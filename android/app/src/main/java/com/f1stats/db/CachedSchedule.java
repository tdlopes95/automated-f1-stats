package com.f1stats.db;

import androidx.room.Entity;

@Entity(tableName = "cached_schedule", primaryKeys = {"year", "round"})
public class CachedSchedule {
    public int year;
    public int round;
    public String raceName;
    public String circuit;
    public String country;
    public String locality;
    public String sessionsJson;
    public long fetchedAt;
}
