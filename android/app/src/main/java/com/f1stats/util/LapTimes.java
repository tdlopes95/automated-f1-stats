package com.f1stats.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Lap time formatting and filtering for the race analysis charts. */
public final class LapTimes {

    /** A lap this much slower than the driver's median is a pit, safety car or incident lap. */
    public static final int SLOW_LAP_PERCENT = 15;

    private LapTimes() {}

    /** 83456 ms -> "1:23.5" (m:ss.s, rounded to the tenth). Negative input -> "". */
    @NonNull
    public static String formatLapTime(long ms) {
        if (ms < 0) return "";
        long tenths = Math.round(ms / 100.0);
        long minutes = tenths / 600;
        long rest = tenths % 600;
        return String.format(Locale.US, "%d:%02d.%d", minutes, rest / 10, rest % 10);
    }

    /** A duration under a minute as seconds ("22.3"), longer ones as {@link #formatLapTime}. */
    @NonNull
    public static String formatSeconds(long ms) {
        if (ms < 0) return "";
        long tenths = Math.round(ms / 100.0);
        if (tenths >= 600) return formatLapTime(ms);
        return String.format(Locale.US, "%d.%d", tenths / 10, tenths % 10);
    }

    /** Median of the non-null lap times, or null if there are none. */
    @Nullable
    public static Double median(@NonNull List<Long> laps) {
        List<Long> values = new ArrayList<>();
        for (Long lap : laps) {
            if (lap != null && lap > 0) values.add(lap);
        }
        if (values.isEmpty()) return null;
        Collections.sort(values);
        int mid = values.size() / 2;
        return values.size() % 2 == 1
                ? values.get(mid).doubleValue()
                : (values.get(mid - 1) + values.get(mid)) / 2.0;
    }

    /**
     * A copy of {@code laps} (same length and indices) with laps more than 15% slower than the
     * driver's median replaced by null.
     */
    @NonNull
    public static List<Long> withoutSlowLaps(@NonNull List<Long> laps) {
        List<Long> out = new ArrayList<>(laps);
        Double median = median(laps);
        if (median == null) return out;
        // Compared in percent so a lap exactly at the limit isn't lost to 1.15 rounding
        double limit = median * (100 + SLOW_LAP_PERCENT);
        for (int i = 0; i < out.size(); i++) {
            Long lap = out.get(i);
            if (lap != null && lap * 100.0 > limit) out.set(i, null);
        }
        return out;
    }
}
