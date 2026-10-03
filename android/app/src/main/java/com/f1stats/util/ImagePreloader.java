package com.f1stats.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads images into Glide's disk cache ahead of time (headshots, circuit images), so a
 * later cold start shows them without the network. Each URL is requested at most once per
 * process, and only on an unmetered network.
 */
public final class ImagePreloader {

    private ImagePreloader() {}

    private static final Set<String> requested = ConcurrentHashMap.newKeySet();

    public static void preload(@NonNull Context context, @NonNull Iterable<String> urls) {
        Context app = context.getApplicationContext();
        if (!isUnmetered(app)) return;
        int started = 0;
        for (String url : urls) {
            if (url == null || url.isEmpty() || !requested.add(url)) continue;
            // DATA is what DiskCacheStrategy.AUTOMATIC reads back for remote images
            Glide.with(app).downloadOnly().load(url).submit();
            started++;
        }
        if (started > 0) DebugLog.d("ImagePreloader", "preloading " + started + " images");
    }

    private static boolean isUnmetered(Context app) {
        ConnectivityManager cm = app.getSystemService(ConnectivityManager.class);
        if (cm == null) return false;
        @Nullable NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }
}
