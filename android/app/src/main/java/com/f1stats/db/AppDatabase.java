package com.f1stats.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {
        CachedSchedule.class,
        CachedResult.class,
        CachedStandings.class,
        CachedDriver.class,
        CachedSessionKey.class,
        CachedMeeting.class,
        CachedCircuitStats.class,
        CachedRaceAnalysis.class,
        CachedTrackMap.class,
        CachedJson.class
    },
    version = 7,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    public abstract ScheduleDao scheduleDao();
    public abstract ResultDao resultDao();
    public abstract StandingsDao standingsDao();
    public abstract DriverDao driverDao();
    public abstract SessionKeyDao sessionKeyDao();
    public abstract MeetingDao meetingDao();
    public abstract CircuitStatsDao circuitStatsDao();
    public abstract RaceAnalysisDao raceAnalysisDao();
    public abstract TrackMapDao trackMapDao();
    public abstract CachedJsonDao cachedJsonDao();

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_circuit_stats` " +
                "(`circuitId` TEXT NOT NULL, `jsonData` TEXT, `cachedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`circuitId`))"
            );
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "f1stats.db"
                    ).addMigrations(MIGRATION_1_2)
                     // Every table is a cache; v3 wiped rows poisoned by empty/future results,
                     // v4 adds cached_meetings.dateStart, v5 adds cached_race_analysis,
                     // v6 adds cached_track_map, v7 adds cached_json
                     .fallbackToDestructiveMigration()
                     .build();
                }
            }
        }
        return instance;
    }
}
