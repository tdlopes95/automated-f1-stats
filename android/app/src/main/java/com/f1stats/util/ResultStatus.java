package com.f1stats.util;

import androidx.annotation.Nullable;

/**
 * Classifies Jolpica result status strings. Shared by H2H, driver profile and season
 * stats so they all agree on what counts as a finish, a non-start and a DNF.
 */
public final class ResultStatus {

    private ResultStatus() {}

    /** "Finished", "+N Laps"/"+1 Lap", or Jolpica's 2024+ "Lapped". */
    public static boolean isFinished(@Nullable String status) {
        if (status == null) return false;
        String s = status.trim();
        return s.equals("Finished") || s.startsWith("+") || s.contains("Lap");
    }

    public static boolean didNotStart(@Nullable String status) {
        if (status == null) return false;
        String s = status.trim();
        return s.equalsIgnoreCase("Did not start")
                || s.equalsIgnoreCase("Withdrew")
                || s.equalsIgnoreCase("Did not qualify")
                || s.equalsIgnoreCase("Did not prequalify")
                || s.equalsIgnoreCase("Excluded");
    }

    /** Disqualified after the race. Also a DNF by {@link #isDnf}, so check this first. */
    public static boolean isDisqualified(@Nullable String status) {
        return status != null && status.trim().equalsIgnoreCase("Disqualified");
    }

    /** Started but not classified as a finisher. A missing status is never a DNF. */
    public static boolean isDnf(@Nullable String status) {
        if (status == null || status.trim().isEmpty()) return false;
        return !isFinished(status) && !didNotStart(status);
    }
}
