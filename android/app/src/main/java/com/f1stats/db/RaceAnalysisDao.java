package com.f1stats.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface RaceAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedRaceAnalysis analysis);

    @Query("SELECT * FROM cached_race_analysis WHERE year = :year AND round = :round LIMIT 1")
    CachedRaceAnalysis get(int year, int round);
}
