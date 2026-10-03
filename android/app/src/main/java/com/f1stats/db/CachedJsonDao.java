package com.f1stats.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface CachedJsonDao {
    @Query("SELECT * FROM cached_json WHERE `key` = :key LIMIT 1")
    CachedJson get(String key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(CachedJson row);

    @Query("DELETE FROM cached_json WHERE `key` = :key")
    void delete(String key);

    /** Drops every key starting with {@code prefix} except {@code keep}. */
    @Query("DELETE FROM cached_json WHERE `key` LIKE :prefix || '%' AND `key` != :keep")
    void deleteOthersWithPrefix(String prefix, String keep);
}
