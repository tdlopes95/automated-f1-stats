package com.f1stats.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
    entities = {
        CachedSchedule.class,
        CachedResult.class,
        CachedStandings.class,
        CachedDriver.class,
        CachedSessionKey.class,
        CachedMeeting.class
    },
    version = 1,
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

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "f1stats.db"
                    ).build();
                }
            }
        }
        return instance;
    }
}
