package com.f1stats.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

import com.f1stats.R;

/** Opens web links in a Chrome Custom Tab, falling back to any app that handles the URL. */
public final class LinkOpener {

    private LinkOpener() {}

    public static void open(@NonNull Context context, @Nullable String url) {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
        Uri uri = Uri.parse(url);
        try {
            int toolbar = ContextCompat.getColor(context, R.color.bg_surface);
            CustomTabsIntent tab = new CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(new CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(toolbar)
                            .setNavigationBarColor(toolbar)
                            .build())
                    .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                    .setShowTitle(true)
                    .build();
            tab.launchUrl(context, uri);
        } catch (ActivityNotFoundException e) {
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(context, R.string.about_no_browser, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
