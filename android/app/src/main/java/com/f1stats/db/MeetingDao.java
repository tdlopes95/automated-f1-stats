package com.f1stats.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MeetingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedMeeting meeting);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CachedMeeting> meetings);

    @Query("SELECT * FROM cached_meetings WHERE meetingKey = :meetingKey LIMIT 1")
    CachedMeeting get(int meetingKey);

    @Query("SELECT * FROM cached_meetings WHERE year = :year")
    List<CachedMeeting> getByYear(int year);

    @Query("DELETE FROM cached_meetings WHERE year = :year")
    void deleteByYear(int year);
}
