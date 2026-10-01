package com.f1stats.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.DateHelper;
import com.f1stats.R;

/** "5 min ago"-style ages for headlines; a date once something is a week old. */
public final class RelativeTime {

    public enum Unit { NOW, MINUTES, HOURS, DAYS, DATE }

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;
    private static final long WEEK_MS = 7 * DAY_MS;

    public final Unit unit;
    /** Whole units elapsed (0 for NOW and DATE). */
    public final long value;

    private RelativeTime(Unit unit, long value) {
        this.unit = unit;
        this.value = value;
    }

    /** The age of {@code thenMs} at {@code nowMs}. A time in the future (clock skew) is NOW. */
    @NonNull
    public static RelativeTime between(long thenMs, long nowMs) {
        long age = nowMs - thenMs;
        if (age < MINUTE_MS) return new RelativeTime(Unit.NOW, 0);
        if (age < HOUR_MS)   return new RelativeTime(Unit.MINUTES, age / MINUTE_MS);
        if (age < DAY_MS)    return new RelativeTime(Unit.HOURS, age / HOUR_MS);
        if (age < WEEK_MS)   return new RelativeTime(Unit.DAYS, age / DAY_MS);
        return new RelativeTime(Unit.DATE, 0);
    }

    /** Formats an ISO-8601 UTC time relative to now; null if it's missing or unparseable. */
    @Nullable
    public static String format(@NonNull Context context, @Nullable String isoUtc, long nowMs) {
        long then = DateHelper.toMillis(isoUtc);
        if (then < 0) return null;
        RelativeTime age = between(then, nowMs);
        switch (age.unit) {
            case NOW:     return context.getString(R.string.time_just_now);
            case MINUTES: return context.getString(R.string.time_minutes_ago, age.value);
            case HOURS:   return context.getString(R.string.time_hours_ago, age.value);
            case DAYS:    return context.getString(R.string.time_days_ago, age.value);
            default:      return DateHelper.formatForDisplay(isoUtc, "d MMM");
        }
    }
}
