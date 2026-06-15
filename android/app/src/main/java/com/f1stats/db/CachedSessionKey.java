package com.f1stats.db;

import androidx.room.Entity;

@Entity(tableName = "cached_session_keys", primaryKeys = {"year", "round"})
public class CachedSessionKey {
    public int year;
    public int round;
    public int sessionKey;
}
