package com.f1stats.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface TrackMapDao {

    @Query("SELECT * FROM cached_track_map WHERE circuitId = :circuitId LIMIT 1")
    CachedTrackMap get(String circuitId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedTrackMap map);
}
