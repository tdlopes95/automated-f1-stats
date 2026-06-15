package com.f1stats.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedResult result);

    @Query("SELECT * FROM cached_results WHERE year = :year AND round = :round AND sessionType = :sessionType LIMIT 1")
    CachedResult get(int year, int round, String sessionType);

    @Query("SELECT * FROM cached_results WHERE year = :year AND round = :round")
    List<CachedResult> getByRound(int year, int round);

    @Query("SELECT * FROM cached_results WHERE year = :year")
    List<CachedResult> getByYear(int year);

    @Query("DELETE FROM cached_results WHERE year = :year")
    void deleteByYear(int year);
}
