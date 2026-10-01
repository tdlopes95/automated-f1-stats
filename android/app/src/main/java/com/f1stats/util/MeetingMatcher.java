package com.f1stats.util;


import androidx.annotation.Nullable;

import com.f1stats.DateHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Matches a Jolpica schedule race to its OpenF1 meeting (circuit image, flag).
 * Matches by date window, validated by country, so a relocated race (e.g. 2026 round 16,
 * "Bahrain Grand Prix" held at Sepang) never picks up the wrong circuit image.
 */
public final class MeetingMatcher {

    private static final String TAG = "CIRCUIT_DEBUG";
    private static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

    private MeetingMatcher() {}

    /**
     * Returns the meeting whose date_start falls between (first session - 2 days) and
     * (Race session + 1 day), or null. Falls back to exact (case-insensitive) name equality
     * only when dates are unavailable. A candidate whose country doesn't match the race's
     * country is rejected.
     */
    @Nullable
    public static Map<String, Object> match(@Nullable Map<String, Object> race,
                                            @Nullable List<Map<String, Object>> meetings) {
        if (race == null || meetings == null || meetings.isEmpty()) return null;

        List<Map<String, Object>> candidates = new ArrayList<>();
        long[] window = raceWindow(race);
        if (window != null && anyMeetingHasDate(meetings)) {
            for (Map<String, Object> m : meetings) {
                long start = DateHelper.toMillis(str(m.get("date_start")));
                if (start > 0 && start >= window[0] && start <= window[1]) candidates.add(m);
            }
        } else {
            String raceName = str(race.get("race_name"));
            if (raceName != null && !raceName.trim().isEmpty()) {
                for (Map<String, Object> m : meetings) {
                    String meetingName = str(m.get("meeting_name"));
                    if (meetingName != null && meetingName.trim().equalsIgnoreCase(raceName.trim())) {
                        candidates.add(m);
                    }
                }
            }
        }
        if (candidates.isEmpty()) return null;

        String raceCountry = normaliseCountry(str(race.get("country")));
        for (Map<String, Object> m : candidates) {
            String meetingCountry = normaliseCountry(str(m.get("country_name")));
            // Can't validate without both sides — accept
            if (raceCountry.isEmpty() || meetingCountry.isEmpty() || raceCountry.equals(meetingCountry)) {
                return m;
            }
        }

        DebugLog.d(TAG, "meeting country mismatch for round " + toInt(race.get("round"))
                + ": openf1=" + str(candidates.get(0).get("country_name"))
                + ", jolpica=" + str(race.get("country")));
        return null;
    }

    /**
     * Copies circuit_image/country_flag from the matched meeting. Without a match (no meeting,
     * or rejected for country mismatch) the image is cleared and the flag falls back to
     * {@link Flags} using the Jolpica country.
     */
    public static void apply(Map<String, Object> race, @Nullable List<Map<String, Object>> meetings) {
        Map<String, Object> meeting = match(race, meetings);
        if (meeting != null) {
            race.put("circuit_image", meeting.get("circuit_image"));
        } else {
            race.remove("circuit_image");
        }
        String flag = flagFor(race, meeting);
        if (flag != null) race.put("country_flag", flag);
        else race.remove("country_flag");
    }

    /** OpenF1 meeting flag if present, else a flagcdn URL for the Jolpica race country. */
    @Nullable
    public static String flagFor(@Nullable Map<String, Object> race,
                                 @Nullable Map<String, Object> meeting) {
        if (meeting != null) {
            String flag = str(meeting.get("country_flag"));
            if (flag != null && !flag.trim().isEmpty()) return flag;
        }
        return race != null ? Flags.urlFor(str(race.get("country"))) : null;
    }

    @Nullable
    private static long[] raceWindow(Map<String, Object> race) {
        Object sessObj = race.get("sessions");
        if (!(sessObj instanceof List)) return null;
        long first = Long.MAX_VALUE;
        long raceTime = -1;
        for (Object s : (List<?>) sessObj) {
            if (!(s instanceof Map)) continue;
            Map<?, ?> session = (Map<?, ?>) s;
            long t = DateHelper.toMillis(str(session.get("datetime")));
            if (t <= 0) continue;
            if (t < first) first = t;
            if ("Race".equals(session.get("name"))) raceTime = t;
        }
        if (raceTime <= 0) return null;
        return new long[]{first - 2 * ONE_DAY_MS, raceTime + ONE_DAY_MS};
    }

    private static boolean anyMeetingHasDate(List<Map<String, Object>> meetings) {
        for (Map<String, Object> m : meetings) {
            if (DateHelper.toMillis(str(m.get("date_start"))) > 0) return true;
        }
        return false;
    }

    static String normaliseCountry(@Nullable String country) {
        if (country == null) return "";
        String c = country.trim().toLowerCase(Locale.ROOT);
        switch (c) {
            case "usa":
            case "united states":
            case "united states of america":
                return "united states";
            case "uk":
            case "united kingdom":
            case "great britain":
                return "united kingdom";
            case "uae":
            case "united arab emirates":
                return "united arab emirates";
            default:
                return c;
        }
    }

    @Nullable
    private static String str(@Nullable Object val) {
        return val != null ? val.toString() : null;
    }

    private static int toInt(@Nullable Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (Exception ignored) {}
        }
        return 0;
    }
}
