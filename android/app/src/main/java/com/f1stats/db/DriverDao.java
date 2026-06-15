package com.f1stats.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DriverDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedDriver driver);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CachedDriver> drivers);

    @Query("SELECT * FROM cached_drivers WHERE driverId = :driverId AND seasonYear = :seasonYear LIMIT 1")
    CachedDriver get(String driverId, int seasonYear);

    @Query("SELECT * FROM cached_drivers WHERE seasonYear = :seasonYear")
    List<CachedDriver> getBySeason(int seasonYear);

    @Query("DELETE FROM cached_drivers WHERE seasonYear = :seasonYear")
    void deleteBySeason(int seasonYear);
}
