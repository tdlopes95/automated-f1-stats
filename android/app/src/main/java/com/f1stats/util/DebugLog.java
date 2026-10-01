package com.f1stats.util;

import android.util.Log;

import com.f1stats.BuildConfig;

/** Debug-only logging. Stripped to a no-op branch in release builds. */
public final class DebugLog {

    private DebugLog() {}

    public static void d(String tag, String msg) {
        if (BuildConfig.DEBUG) Log.d(tag, msg);
    }
}
