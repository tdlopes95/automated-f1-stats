package com.f1stats.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SessionKeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedSessionKey sessionKey);

    @Query("SELECT * FROM cached_session_keys WHERE year = :year AND round = :round LIMIT 1")
    CachedSessionKey get(int year, int round);

    @Query("DELETE FROM cached_session_keys WHERE year = :year")
    void deleteByYear(int year);
}
