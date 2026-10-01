package com.f1stats;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import okhttp3.HttpUrl;

public class SettingsManager {

    private static final String PREFS_NAME = "f1stats_prefs";
    private static final String KEY_BASE_URL = "base_url";
    public static final String DEFAULT_URL = BuildConfig.DEFAULT_BASE_URL;

    private static SettingsManager instance;
    private final SharedPreferences prefs;

    private SettingsManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context);
        }
        return instance;
    }

    public String getBaseUrl() {
        String stored = prefs.getString(KEY_BASE_URL, "");
        // Old builds defaulted to an ngrok tunnel that no longer exists — migrate to the default
        if (stored == null || stored.trim().isEmpty() || stored.contains("ngrok")) {
            setBaseUrl(DEFAULT_URL);
            return DEFAULT_URL;
        }
        String url = normaliseUrl(stored);
        return url != null ? url : DEFAULT_URL;
    }

    public void setBaseUrl(String url) {
        prefs.edit().putString(KEY_BASE_URL, url).apply();
    }

    /**
     * Returns the URL with a trailing "/" if it is valid for this build, otherwise null.
     * Debug builds accept http and https; release builds are HTTPS-only (no cleartext config).
     */
    @Nullable
    public static String normaliseUrl(@Nullable String raw) {
        return normaliseUrl(raw, BuildConfig.DEBUG);
    }

    /**
     * Returns the URL with a trailing "/" if it is a valid https URL (or http, when allowHttp),
     * otherwise null. Retrofit requires base URLs to end with "/".
     */
    @Nullable
    public static String normaliseUrl(@Nullable String raw, boolean allowHttp) {
        if (raw == null) return null;
        String url = raw.trim();
        boolean http = url.startsWith("http://");
        if (!url.startsWith("https://") && !(allowHttp && http)) return null;
        if (HttpUrl.parse(url) == null) return null;
        if (!url.endsWith("/")) url = url + "/";
        return url;
    }
}
