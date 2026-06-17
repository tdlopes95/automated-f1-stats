package com.f1stats.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface CircuitStatsDao {

    @Query("SELECT * FROM cached_circuit_stats WHERE circuitId = :circuitId LIMIT 1")
    CachedCircuitStats get(String circuitId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedCircuitStats stats);
}
