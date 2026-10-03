package com.f1stats.widget;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.R;
import com.f1stats.util.ResultStatus;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The favourite driver widget's text, from a {@link WidgetSnapshot}. Templates come from
 * strings.xml at runtime and are passed in directly by JVM tests.
 */
public final class WidgetText {

    private WidgetText() {}

    /** Sessions further away than this show their date as well as the weekday. */
    static final long DATE_AFTER_MS = 6 * 24 * 60 * 60 * 1000L;

    /** Format strings, in strings.xml order. */
    public static final class Templates {
        /** "Choose a favourite driver" */
        final String chooseDriver;
        /** position, points: "P%1$d · %2$s pts" */
        final String positionPoints;
        /** "Not in the standings yet" */
        final String notInStandings;
        /** "No data yet" (a new favourite whose data hasn't loaded) */
        final String noData;
        /** "Leader" */
        final String leader;
        /** gap, position ahead: "−%1$s to P%2$d" */
        final String gapToAhead;
        /** result, race: "Last race: %1$s · %2$s" */
        final String lastRace;
        /** "No races yet" */
        final String noRaces;
        /** position: "P%1$d" */
        final String position;
        final String dnf;
        final String dns;
        /** session, day and time: "Next: %1$s · %2$s" */
        final String nextSession;
        /** "No upcoming sessions" */
        final String noNextSession;

        public Templates(String chooseDriver, String positionPoints, String notInStandings,
                         String noData, String leader, String gapToAhead, String lastRace,
                         String noRaces, String position, String dnf, String dns,
                         String nextSession, String noNextSession) {
            this.chooseDriver = chooseDriver;
            this.positionPoints = positionPoints;
            this.notInStandings = notInStandings;
            this.noData = noData;
            this.leader = leader;
            this.gapToAhead = gapToAhead;
            this.lastRace = lastRace;
            this.noRaces = noRaces;
            this.position = position;
            this.dnf = dnf;
            this.dns = dns;
            this.nextSession = nextSession;
            this.noNextSession = noNextSession;
        }

        @NonNull
        public static Templates from(@NonNull Context context) {
            return new Templates(
                    context.getString(R.string.widget_choose_driver),
                    context.getString(R.string.widget_position_points),
                    context.getString(R.string.widget_not_in_standings),
                    context.getString(R.string.widget_no_data),
                    context.getString(R.string.home_leader),
                    context.getString(R.string.home_gap_to_ahead),
                    context.getString(R.string.widget_last_race),
                    context.getString(R.string.home_form_none),
                    context.getString(R.string.home_position),
                    context.getString(R.string.home_form_dnf),
                    context.getString(R.string.home_form_dns),
                    context.getString(R.string.widget_next_session),
                    context.getString(R.string.widget_no_next_session));
        }
    }

    /** What each widget view shows. Empty strings for rows with nothing to say. */
    public static final class Lines {
        /** No favourite: only {@link #prompt} is shown and a tap opens the driver picker. */
        public boolean chooseDriver;
        public String prompt = "";
        public String name = "";
        public String positionPoints = "";
        public String gap = "";
        public String lastRace = "";
        public String nextSession = "";
    }

    /**
     * @param favouriteId   the current favourite (HomeLayoutStore), which wins over the
     *                      snapshot's: a snapshot for another driver is out of date
     * @param favouriteName the favourite's cached display name, used until data loads
     */
    @NonNull
    public static Lines build(@NonNull Templates t, @Nullable WidgetSnapshot snapshot,
                              @Nullable String favouriteId, @Nullable String favouriteName,
                              long now, boolean use24Hour, @NonNull TimeZone zone,
                              @NonNull Locale locale) {
        Lines out = new Lines();
        if (favouriteId == null || favouriteId.isEmpty()) {
            out.chooseDriver = true;
            out.prompt = t.chooseDriver;
            return out;
        }

        boolean current = snapshot != null && favouriteId.equals(snapshot.driverId);
        out.name = current && snapshot.name != null ? snapshot.name
                : favouriteName != null ? favouriteName : favouriteId;
        if (!current) {
            out.positionPoints = t.noData;
        } else if (!snapshot.inStandings) {
            out.positionPoints = t.notInStandings;
        } else {
            out.positionPoints = String.format(locale, t.positionPoints,
                    snapshot.position, formatPoints(snapshot.points));
            out.gap = gap(t, snapshot.position, snapshot.gapToAhead, locale);
        }
        if (current) out.lastRace = lastRace(t, snapshot, locale);
        out.nextSession = nextSession(t, snapshot, now, use24Hour, zone, locale);
        return out;
    }

    /** "Leader", or "−12 to P2" for the gap to the driver one place ahead. */
    @NonNull
    static String gap(@NonNull Templates t, int position, double gapToAhead, @NonNull Locale locale) {
        if (position <= 1) return t.leader;
        return String.format(locale, t.gapToAhead, formatPoints(gapToAhead), position - 1);
    }

    @NonNull
    static String lastRace(@NonNull Templates t, @NonNull WidgetSnapshot s, @NonNull Locale locale) {
        if (s.lastRaceName == null) return t.noRaces;
        String result;
        if (ResultStatus.didNotStart(s.lastRaceStatus)) result = t.dns;
        else if (ResultStatus.isDnf(s.lastRaceStatus) || s.lastRacePosition <= 0) result = t.dnf;
        else result = String.format(locale, t.position, s.lastRacePosition);
        return String.format(locale, t.lastRace, result, s.lastRaceName);
    }

    /** "Next: Qualifying · Sat 14:00"; the date is added when it's more than six days away. */
    @NonNull
    static String nextSession(@NonNull Templates t, @Nullable WidgetSnapshot s, long now,
                              boolean use24Hour, @NonNull TimeZone zone, @NonNull Locale locale) {
        WidgetSnapshot.Session next = s != null ? s.nextSession(now) : null;
        if (next == null) return t.noNextSession;
        String day = next.startMillis - now > DATE_AFTER_MS ? "EEE d MMM" : "EEE";
        SimpleDateFormat format = new SimpleDateFormat(
                day + (use24Hour ? " HH:mm" : " h:mm a"), locale);
        format.setTimeZone(zone);
        return String.format(locale, t.nextSession, next.name,
                format.format(new Date(next.startMillis)));
    }

    /** "25" rather than "25.0"; half points kept. */
    @NonNull
    static String formatPoints(double points) {
        if (points == Math.rint(points)) return String.valueOf((long) points);
        return String.valueOf(points);
    }
}
