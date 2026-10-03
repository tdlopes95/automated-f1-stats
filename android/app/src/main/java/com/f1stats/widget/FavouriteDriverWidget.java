package com.f1stats.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/**
 * Home-screen widget for the favourite driver: standing, gap ahead, last race and the next
 * session. Renders from {@link WidgetSnapshot}; see {@link FavouriteWidgetUpdater}.
 */
public class FavouriteDriverWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        // Idempotent (KEEP), so this also covers app updates and restored widgets
        WidgetRefreshWorker.schedulePeriodic(context);
        Context app = context.getApplicationContext();
        // Called from within onReceive, so goAsync() is valid here
        PendingResult result = goAsync();
        FavouriteWidgetUpdater.renderAsync(app, () -> {
            try {
                if (FavouriteWidgetUpdater.isStale(app)) WidgetRefreshWorker.refreshOnce(app);
            } finally {
                result.finish();
            }
        });
    }

    @Override
    public void onDisabled(Context context) {
        WidgetRefreshWorker.cancel(context);
    }
}
