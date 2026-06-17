package com.f1stats.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedSchedule schedule);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CachedSchedule> schedules);

    @Query("SELECT * FROM cached_schedule WHERE year = :year AND round = :round LIMIT 1")
    CachedSchedule get(int year, int round);

    @Query("SELECT * FROM cached_schedule WHERE year = :year ORDER BY round ASC")
    List<CachedSchedule> getByYear(int year);

    @Query("SELECT * FROM cached_schedule WHERE circuit = :circuit ORDER BY year DESC, round DESC")
    List<CachedSchedule> getByCircuit(String circuit);

    @Query("DELETE FROM cached_schedule WHERE year = :year")
    void deleteByYear(int year);
}
