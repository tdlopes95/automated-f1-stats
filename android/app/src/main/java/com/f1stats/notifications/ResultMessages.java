package com.f1stats.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.R;
import com.f1stats.models.RaceResult;
import com.f1stats.util.ResultStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the favourite driver / team lines of a result notification. Templates come from
 * strings.xml at runtime and are passed in directly by JVM tests.
 */
public final class ResultMessages {

    private ResultMessages() {}

    /** Format strings, in strings.xml order. */
    public static final class Templates {
        /** name, position, race: "%1$s finished P%2$s at the %3$s" */
        final String finished;
        /** name, position, race: "%1$s finished P%2$s in the %3$s sprint" */
        final String finishedSprint;
        /** name, race ref: "%1$s retired from the %2$s" */
        final String retired;
        /** name, race ref: "%1$s did not start the %2$s" */
        final String didNotStart;
        /** name, race ref: "%1$s was disqualified from the %2$s" */
        final String disqualified;
        /** race name: "%1$s sprint" (the race ref for sprint lines) */
        final String sprintRef;
        /** points: " (+%1$s pts)" */
        final String points;
        /** team, entries: "%1$s: %2$s" */
        final String team;
        /** code, position: "%1$s P%2$s" */
        final String teamEntry;
        /** code, label (DNF/DNS/DSQ): "%1$s %2$s" */
        final String teamEntryOut;
        final String labelDnf;
        final String labelDns;
        final String labelDsq;

        public Templates(String finished, String finishedSprint, String retired,
                         String didNotStart, String disqualified, String sprintRef,
                         String points, String team, String teamEntry, String teamEntryOut,
                         String labelDnf, String labelDns, String labelDsq) {
            this.finished = finished;
            this.finishedSprint = finishedSprint;
            this.retired = retired;
            this.didNotStart = didNotStart;
            this.disqualified = disqualified;
            this.sprintRef = sprintRef;
            this.points = points;
            this.team = team;
            this.teamEntry = teamEntry;
            this.teamEntryOut = teamEntryOut;
            this.labelDnf = labelDnf;
            this.labelDns = labelDns;
            this.labelDsq = labelDsq;
        }

        @NonNull
        public static Templates from(@NonNull Context context) {
            return new Templates(
                    context.getString(R.string.notif_result_finished),
                    context.getString(R.string.notif_result_finished_sprint),
                    context.getString(R.string.notif_result_retired),
                    context.getString(R.string.notif_result_did_not_start),
                    context.getString(R.string.notif_result_disqualified),
                    context.getString(R.string.notif_result_sprint_ref),
                    context.getString(R.string.notif_result_points),
                    context.getString(R.string.notif_result_team),
                    context.getString(R.string.notif_result_team_entry),
                    context.getString(R.string.notif_result_team_entry_out),
                    context.getString(R.string.notif_result_label_dnf),
                    context.getString(R.string.notif_result_label_dns),
                    context.getString(R.string.notif_result_label_dsq));
        }
    }

    /**
     * The notification lines: the favourite driver's first, then the favourite team's.
     * Empty if neither appears in the results.
     */
    @NonNull
    public static List<String> lines(@NonNull Templates t, @NonNull List<RaceResult> results,
                                     @Nullable String driverId, @Nullable String constructorId,
                                     @NonNull String raceName, boolean sprint) {
        List<String> out = new ArrayList<>();
        String driver = driverLine(t, results, driverId, raceName, sprint);
        if (driver != null) out.add(driver);
        String team = teamLine(t, results, constructorId);
        if (team != null) out.add(team);
        return out;
    }

    /** "{name} finished P{pos} at the {race}" (+ points), or the retired/DNS/DSQ variant. */
    @Nullable
    public static String driverLine(@NonNull Templates t, @NonNull List<RaceResult> results,
                                    @Nullable String driverId, @NonNull String raceName,
                                    boolean sprint) {
        if (driverId == null || driverId.isEmpty()) return null;
        for (RaceResult r : results) {
            RaceResult.Driver d = r.getDriver();
            if (d == null || !driverId.equals(d.getDriverId())) continue;

            String name = d.getFullName();
            String raceRef = sprint ? String.format(t.sprintRef, raceName) : raceName;
            String status = r.getStatus();
            if (isDisqualified(status)) return String.format(t.disqualified, name, raceRef);
            if (ResultStatus.didNotStart(status)) return String.format(t.didNotStart, name, raceRef);
            if (ResultStatus.isDnf(status)) return String.format(t.retired, name, raceRef);
            if (r.getPosition() == null) return null;

            String line = String.format(sprint ? t.finishedSprint : t.finished,
                    name, r.getPosition(), raceName);
            String points = formatPoints(r.getPoints());
            return points != null ? line + String.format(t.points, points) : line;
        }
        return null;
    }

    /** "{team}: {code} P{x}, {code} P{y}" in finishing order; non-finishers show DNF/DNS/DSQ. */
    @Nullable
    public static String teamLine(@NonNull Templates t, @NonNull List<RaceResult> results,
                                  @Nullable String constructorId) {
        if (constructorId == null || constructorId.isEmpty()) return null;
        List<RaceResult> team = new ArrayList<>();
        String teamName = null;
        for (RaceResult r : results) {
            RaceResult.Constructor c = r.getConstructor();
            if (c == null || !constructorId.equals(c.getConstructorId())) continue;
            team.add(r);
            if (teamName == null) teamName = c.getName();
        }
        if (team.isEmpty()) return null;
        team.sort(Comparator.comparingInt(r -> parsePosition(r.getPosition())));

        StringBuilder entries = new StringBuilder();
        for (RaceResult r : team) {
            if (entries.length() > 0) entries.append(", ");
            entries.append(teamEntry(t, r));
        }
        return String.format(t.team, teamName != null ? teamName : constructorId, entries);
    }

    private static String teamEntry(Templates t, RaceResult r) {
        RaceResult.Driver d = r.getDriver();
        String code = d == null ? "?"
                : d.getCode() != null ? d.getCode()
                : d.getLastName() != null ? d.getLastName() : "?";
        String status = r.getStatus();
        if (isDisqualified(status)) return String.format(t.teamEntryOut, code, t.labelDsq);
        if (ResultStatus.didNotStart(status)) return String.format(t.teamEntryOut, code, t.labelDns);
        if (ResultStatus.isDnf(status)) return String.format(t.teamEntryOut, code, t.labelDnf);
        return String.format(t.teamEntry, code, r.getPosition());
    }

    // ResultStatus counts a disqualification as a DNF; the notification says what happened
    private static boolean isDisqualified(@Nullable String status) {
        return status != null && status.trim().equalsIgnoreCase("Disqualified");
    }

    /** "25" → "25", "0.5" → "0.5", "8.0" → "8"; null when no points were scored. */
    @Nullable
    static String formatPoints(@Nullable String points) {
        if (points == null) return null;
        try {
            BigDecimal value = new BigDecimal(points.trim());
            if (value.signum() <= 0) return null;
            return value.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parsePosition(@Nullable String position) {
        try {
            return position != null ? Integer.parseInt(position.trim()) : Integer.MAX_VALUE;
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
