package com.f1stats.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "cached_circuit_stats", primaryKeys = {"circuitId"})
public class CachedCircuitStats {
    @NonNull
    public String circuitId = "";
    public String jsonData;
    public long cachedAt;
}
