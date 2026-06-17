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
        CachedCircuitStats.class
    },
    version = 2,
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
                    ).addMigrations(MIGRATION_1_2).build();
                }
            }
        }
        return instance;
    }
}
