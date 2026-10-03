package com.f1stats.widget;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.R;
import com.f1stats.util.ResultStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.IntFunction;

/**
 * The favourite driver widget's text, from a {@link WidgetSnapshot}. Templates come from
 * strings.xml at runtime and are filled in directly by JVM tests.
 */
public final class WidgetText {

    private WidgetText() {}

    /** Sessions further away than this show their date as well as the weekday. */
    static final long DATE_AFTER_MS = 6 * 24 * 60 * 60 * 1000L;
    /** Form chips shown on the large widget. */
    static final int FORM_SIZE = 5;
    /** Finishing positions that score points. */
    static final int POINTS_POSITIONS = 10;

    /** Format strings and plurals; see strings.xml for each one's arguments. */
    public static final class Templates {
        /** "Choose a favourite driver" */
        String chooseDriver;
        /** "Not in the standings yet" */
        String notInStandings;
        /** "No data yet" (a new favourite whose data hasn't loaded) */
        String noData;
        /** points: "%1$s pts" */
        String points;
        /** "Leader" */
        String leader;
        /** gap, position ahead: "−%1$s to P%2$d" */
        String gapToAhead;
        /** gap: "−%1$s to leader" */
        String gapToLeader;
        /** wins, podiums: "%1$s · %2$s" */
        String winsPodiums;
        /** count: "%d win(s)" */
        IntFunction<String> wins;
        /** count: "%d podium(s)" */
        IntFunction<String> podiums;
        /** result: "Last: %1$s" */
        String lastResult;
        /** "No races yet" */
        String noRaces;
        /** position: "P%1$d" */
        String position;
        String dnf;
        String dns;
        String dsq;
        /** session, day and time: "Next: %1$s · %2$s" */
        String nextSession;
        /** "No upcoming sessions" */
        String noNextSession;
        /** teammate, driver's wins, teammate's wins: "H2H vs %1$s: %2$d - %3$d" */
        String h2h;

        @NonNull
        public static Templates from(@NonNull Context context) {
            Templates t = new Templates();
            t.chooseDriver = context.getString(R.string.widget_choose_driver);
            t.notInStandings = context.getString(R.string.widget_not_in_standings);
            t.noData = context.getString(R.string.widget_no_data);
            t.points = context.getString(R.string.home_points_value);
            t.leader = context.getString(R.string.home_leader);
            t.gapToAhead = context.getString(R.string.home_gap_to_ahead);
            t.gapToLeader = context.getString(R.string.widget_gap_to_leader);
            t.winsPodiums = context.getString(R.string.widget_wins_podiums);
            t.wins = n -> context.getResources().getQuantityString(R.plurals.widget_wins, n, n);
            t.podiums = n -> context.getResources().getQuantityString(R.plurals.widget_podiums, n, n);
            t.lastResult = context.getString(R.string.widget_last_result);
            t.noRaces = context.getString(R.string.home_form_none);
            t.position = context.getString(R.string.home_position);
            t.dnf = context.getString(R.string.home_form_dnf);
            t.dns = context.getString(R.string.home_form_dns);
            t.dsq = context.getString(R.string.widget_form_dsq);
            t.nextSession = context.getString(R.string.widget_next_session);
            t.noNextSession = context.getString(R.string.widget_no_next_session);
            t.h2h = context.getString(R.string.widget_h2h);
            return t;
        }
    }

    /** How a form chip is coloured. */
    public enum ChipKind {
        /** P1 to P3: highlighted */
        PODIUM,
        /** P4 to P10 */
        POINTS,
        /** Classified outside the points */
        FINISH,
        /** DNF, DNS or DSQ: muted */
        RETIRED
    }

    public static final class Chip {
        public final String label;
        public final ChipKind kind;
        @Nullable public final String raceName;

        Chip(String label, ChipKind kind, @Nullable String raceName) {
            this.label = label;
            this.kind = kind;
            this.raceName = raceName;
        }
    }

    /** What each widget view shows. Empty strings for rows with nothing to say. */
    public static final class Lines {
        /** No favourite: only {@link #prompt} is shown and a tap opens the driver picker. */
        public boolean chooseDriver;
        public String prompt = "";
        public String name = "";
        /** Three-letter code, or the name when there is none. */
        public String code = "";
        public String team = "";
        /** "P3"; empty when there is no standing, and {@link #points} says why. */
        public String position = "";
        public String points = "";
        /** "Leader" or "−12 to P2" */
        public String gap = "";
        /** "−45 to leader"; empty for P1 and P2, where {@link #gap} already says it. */
        public String gapToLeader = "";
        public String winsPodiums = "";
        /** "Last: P2" */
        public String lastResult = "";
        public String nextSession = "";
        public String h2h = "";
        public String nextRaceName = "";
        /** The next race weekend's first session, in local time. */
        public String nextRaceDate = "";
        /** Last results, oldest first, at most {@link #FORM_SIZE}. */
        public final List<Chip> form = new ArrayList<>();
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
        out.code = current && snapshot.code != null && !snapshot.code.isEmpty()
                ? snapshot.code : out.name;
        if (!current) {
            out.points = t.noData;
        } else {
            if (snapshot.teamName != null) out.team = snapshot.teamName;
            if (!snapshot.inStandings) {
                out.points = t.notInStandings;
            } else {
                out.position = String.format(locale, t.position, snapshot.position);
                out.points = String.format(locale, t.points, formatPoints(snapshot.points));
                out.gap = gap(t, snapshot.position, snapshot.gapToAhead, locale);
                if (snapshot.position > 2) {
                    out.gapToLeader = String.format(locale, t.gapToLeader,
                            formatPoints(snapshot.gapToLeader));
                }
            }
            out.winsPodiums = String.format(locale, t.winsPodiums,
                    t.wins.apply(snapshot.wins), t.podiums.apply(snapshot.podiums));
            out.lastResult = lastResult(t, snapshot.lastResult(), locale);
            out.form.addAll(form(t, snapshot.lastResults, locale));
            if (snapshot.teammateCode != null && !snapshot.teammateCode.isEmpty()) {
                out.h2h = String.format(locale, t.h2h, snapshot.teammateCode,
                        snapshot.h2hWins, snapshot.h2hLosses);
            }
        }
        out.nextSession = nextSession(t, snapshot, now, use24Hour, zone, locale);
        WidgetSnapshot.Session next = snapshot != null ? snapshot.nextSession(now) : null;
        if (next != null && next.raceName != null) {
            out.nextRaceName = next.raceName;
            out.nextRaceDate = dateTime(next.weekendStart(), now, true, use24Hour, zone, locale);
        }
        return out;
    }

    /** "Leader", or "−12 to P2" for the gap to the driver one place ahead. */
    @NonNull
    static String gap(@NonNull Templates t, int position, double gapToAhead, @NonNull Locale locale) {
        if (position <= 1) return t.leader;
        return String.format(locale, t.gapToAhead, formatPoints(gapToAhead), position - 1);
    }

    /** "Last: P2", "Last: DNF", or "No races yet". */
    @NonNull
    static String lastResult(@NonNull Templates t, @Nullable WidgetSnapshot.Result r,
                             @NonNull Locale locale) {
        if (r == null) return t.noRaces;
        return String.format(locale, t.lastResult, resultLabel(t, r, locale));
    }

    @NonNull
    static List<Chip> form(@NonNull Templates t, @Nullable List<WidgetSnapshot.Result> results,
                           @NonNull Locale locale) {
        List<Chip> out = new ArrayList<>();
        if (results == null) return out;
        int from = Math.max(0, results.size() - FORM_SIZE);
        for (WidgetSnapshot.Result r : results.subList(from, results.size())) {
            if (r == null) continue;
            ChipKind kind;
            if (retired(r)) kind = ChipKind.RETIRED;
            else if (r.position <= 3) kind = ChipKind.PODIUM;
            else if (r.position <= POINTS_POSITIONS) kind = ChipKind.POINTS;
            else kind = ChipKind.FINISH;
            out.add(new Chip(resultLabel(t, r, locale), kind, r.raceName));
        }
        return out;
    }

    private static boolean retired(WidgetSnapshot.Result r) {
        return ResultStatus.didNotStart(r.status) || ResultStatus.isDnf(r.status) || r.position <= 0;
    }

    /** "P2", "DNS", "DSQ" or "DNF". */
    @NonNull
    private static String resultLabel(@NonNull Templates t, @NonNull WidgetSnapshot.Result r,
                                      @NonNull Locale locale) {
        if (ResultStatus.didNotStart(r.status)) return t.dns;
        if (ResultStatus.isDisqualified(r.status)) return t.dsq;
        if (ResultStatus.isDnf(r.status) || r.position <= 0) return t.dnf;
        return String.format(locale, t.position, r.position);
    }

    /** "Next: Qualifying · Sat 14:00"; the date is added when it's more than six days away. */
    @NonNull
    static String nextSession(@NonNull Templates t, @Nullable WidgetSnapshot s, long now,
                              boolean use24Hour, @NonNull TimeZone zone, @NonNull Locale locale) {
        WidgetSnapshot.Session next = s != null ? s.nextSession(now) : null;
        if (next == null) return t.noNextSession;
        return String.format(locale, t.nextSession, next.name,
                dateTime(next.startMillis, now, false, use24Hour, zone, locale));
    }

    /** "Sat 14:00", with the date when far away or {@code alwaysDate}: "Sat 10 Oct 14:00". */
    @NonNull
    private static String dateTime(long millis, long now, boolean alwaysDate, boolean use24Hour,
                                   @NonNull TimeZone zone, @NonNull Locale locale) {
        String day = alwaysDate || millis - now > DATE_AFTER_MS ? "EEE d MMM" : "EEE";
        SimpleDateFormat format = new SimpleDateFormat(
                day + (use24Hour ? " HH:mm" : " h:mm a"), locale);
        format.setTimeZone(zone);
        return format.format(new Date(millis));
    }

    /** "25" rather than "25.0"; half points kept. */
    @NonNull
    static String formatPoints(double points) {
        if (points == Math.rint(points)) return String.valueOf((long) points);
        return String.valueOf(points);
    }
}
