package com.f1stats.widget;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/** Refreshes the favourite driver widget's snapshot: every 6 hours, or once on demand. */
public class WidgetRefreshWorker extends Worker {

    private static final String PERIODIC_WORK = "favourite_widget_periodic";
    private static final String ONCE_WORK = "favourite_widget_once";

    public WidgetRefreshWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedulePeriodic(@NonNull Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                WidgetRefreshWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(networkConstraint())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** E.g. a widget was just added and has no snapshot yet. */
    public static void refreshOnce(@NonNull Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WidgetRefreshWorker.class)
                .setConstraints(networkConstraint())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE_WORK,
                ExistingWorkPolicy.KEEP, request);
    }

    /** The last widget was removed. */
    public static void cancel(@NonNull Context context) {
        WorkManager wm = WorkManager.getInstance(context);
        wm.cancelUniqueWork(PERIODIC_WORK);
        wm.cancelUniqueWork(ONCE_WORK);
    }

    private static Constraints networkConstraint() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    @NonNull
    @Override
    public Result doWork() {
        FavouriteWidgetUpdater.refresh(getApplicationContext());
        return Result.success();
    }
}
