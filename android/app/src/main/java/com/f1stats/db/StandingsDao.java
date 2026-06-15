package com.f1stats.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface StandingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedStandings standings);

    @Query("SELECT * FROM cached_standings WHERE year = :year AND type = :type LIMIT 1")
    CachedStandings get(int year, String type);

    @Query("SELECT * FROM cached_standings WHERE year = :year")
    List<CachedStandings> getByYear(int year);

    @Query("DELETE FROM cached_standings WHERE year = :year")
    void deleteByYear(int year);
}
