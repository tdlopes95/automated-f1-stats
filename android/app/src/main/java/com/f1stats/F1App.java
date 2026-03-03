package com.f1stats;

import android.app.Application;

public class F1App extends Application {

    private static F1App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static F1App get() {
        return instance;
    }
}