package com.f1stats;

import android.app.Application;

import com.f1stats.db.AppDatabase;

public class F1App extends Application {

    private static F1App instance;
    private AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        database = AppDatabase.getInstance(this);
    }

    public static F1App get() {
        return instance;
    }

    public AppDatabase getDatabase() {
        return database;
    }
}
