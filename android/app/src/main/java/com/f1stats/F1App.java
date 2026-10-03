package com.f1stats;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import com.f1stats.api.RequestStats;
import com.f1stats.db.AppDatabase;
import com.f1stats.notifications.Notifications;

public class F1App extends Application {

    private static final long REQUEST_STATS_DELAY_MS = 30_000;

    private static F1App instance;
    private AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        database = AppDatabase.getInstance(this);
        Notifications.createChannels(this);
        if (BuildConfig.DEBUG) {
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> RequestStats.log("30s after start"), REQUEST_STATS_DELAY_MS);
        }
    }

    public static F1App get() {
        return instance;
    }

    public AppDatabase getDatabase() {
        return database;
    }
}
