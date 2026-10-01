package com.f1stats.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.models.RaceResult;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Season stats and race head-to-head from Jolpica Race and Sprint results. Pure Java so it
 * runs in JVM tests; shared by the compare screen and the Home cards.
 *
 * Rules: points include sprints, everything else is race-only. Average finish counts
 * classified finishes only ({@link ResultStatus#isFinished}, so "Lapped" and "+1 Lap"
 * count). The race H2H covers rounds where both drivers started; Jolpica already classifies
 * retirements behind finishers, so position decides.
 */
public final class HeadToHead {

    private HeadToHead() {}

    public static final String SESSION_RACE = "Race";
    public static final String SESSION_SPRINT = "Sprint";

    // ── Input ─────────────────────────────────────────────────────────────────

    /** One round's Race and (on sprint weekends) Sprint results. */
    public static class Round {
        public final int round;
        @Nullable public String raceName;
        @Nullable public List<RaceResult> race;
        @Nullable public List<RaceResult> sprint;

        public Round(int round) {
            this.round = round;
        }
    }

    /** A season's rounds, in round order. */
    public static class Season {
        public final int year;
        private final TreeMap<Integer, Round> rounds = new TreeMap<>();

        public Season(int year) {
            this.year = year;
        }

        @NonNull
        public Round round(int round) {
            Round r = rounds.get(round);
            if (r == null) {
                r = new Round(round);
                rounds.put(round, r);
            }
            return r;
        }

        @NonNull
        public List<Round> getRounds() {
            return new ArrayList<>(rounds.values());
        }

        /**
         * Adds a stored results body (the backend's {@code /results} JSON: "results" plus
         * "race_name"). Other session types and unparseable bodies are ignored.
         */
        public void addResultsJson(int round, String sessionType, @Nullable String json, Gson gson) {
            boolean isRace = SESSION_RACE.equals(sessionType);
            if (!isRace && !SESSION_SPRINT.equals(sessionType)) return;
            if (json == null) return;
            List<RaceResult> results;
            Object raceName;
            try {
                Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> body = gson.fromJson(json, mapType);
                if (body == null) return;
                Object resultsObj = body.get("results");
                if (!(resultsObj instanceof List)) return;
                Type listType = new TypeToken<List<RaceResult>>(){}.getType();
                results = gson.fromJson(gson.toJson(resultsObj), listType);
                raceName = body.get("race_name");
            } catch (RuntimeException e) {
                return;
            }
            if (results == null) return;
            Round r = round(round);
            if (isRace) r.race = results;
            else        r.sprint = results;
            if (raceName != null && r.raceName == null) r.raceName = raceName.toString();
        }
    }

    /** Matches results by Jolpica driverId; by code only when there is no driverId. */
    public static class DriverRef {
        public final String driverId;
        public final String code;

        public DriverRef(@Nullable String driverId, @Nullable String code) {
            this.driverId = driverId != null ? driverId : "";
            this.code     = code != null ? code : "";
        }

        public static DriverRef of(@Nullable String driverId) {
            return new DriverRef(driverId, null);
        }

        public boolean matches(@Nullable RaceResult.Driver rd) {
            if (rd == null) return false;
            if (!driverId.isEmpty()) return driverId.equals(rd.getDriverId());
            return !code.isEmpty() && code.equalsIgnoreCase(rd.getCode());
        }

        @NonNull
        @Override
        public String toString() {
            return "driverId=" + driverId + " code=" + code;
        }
    }

    // ── Output ────────────────────────────────────────────────────────────────

    public static class DriverStats {
        public double points;
        public int wins, podiums, dnfs, poles;
        public int finishCount, finishPositionTotal;
        /** Best grid slot, 0 if the driver never took a grid slot. */
        public int bestGrid;
        /** Race H2H rounds won (pair comparisons only). */
        public int h2hWins;

        /** Average finishing position over classified finishes, 0 if none. */
        public double avgFinish() {
            return finishCount > 0 ? (double) finishPositionTotal / finishCount : 0;
        }
    }

    public static class Comparison {
        public final DriverStats driver1 = new DriverStats();
        public final DriverStats driver2 = new DriverStats();
    }

    /** One race result for the form guide. */
    public static class FormEntry {
        public final int round;
        @Nullable public final String raceName;
        /** Classified position, 0 if none. */
        public final int position;
        @Nullable public final String status;

        FormEntry(int round, @Nullable String raceName, int position, @Nullable String status) {
            this.round = round;
            this.raceName = raceName;
            this.position = position;
            this.status = status;
        }

        public boolean isDnf()        { return ResultStatus.isDnf(status); }
        public boolean didNotStart()  { return ResultStatus.didNotStart(status); }
    }

    public static class DriverSeason {
        public final DriverStats stats = new DriverStats();
        /** Up to N most recent race results, oldest first. */
        public final List<FormEntry> lastResults = new ArrayList<>();
    }

    // ── Computation ───────────────────────────────────────────────────────────

    @NonNull
    public static Comparison compare(@NonNull Season season,
                                     @NonNull DriverRef d1, @NonNull DriverRef d2) {
        Comparison out = new Comparison();
        for (Round round : season.getRounds()) {
            if (round.sprint != null) {
                for (RaceResult r : round.sprint) {
                    if (d1.matches(r.getDriver()))      out.driver1.points += parseDouble(r.getPoints());
                    else if (d2.matches(r.getDriver())) out.driver2.points += parseDouble(r.getPoints());
                }
            }
            if (round.race == null) continue;

            RaceResult r1 = null, r2 = null;
            for (RaceResult r : round.race) {
                if (d1.matches(r.getDriver())) {
                    addRace(out.driver1, r);
                    r1 = r;
                } else if (d2.matches(r.getDriver())) {
                    addRace(out.driver2, r);
                    r2 = r;
                }
            }

            if (r1 != null && r2 != null
                    && !ResultStatus.didNotStart(r1.getStatus())
                    && !ResultStatus.didNotStart(r2.getStatus())) {
                int pos1 = parseInt(r1.getPosition());
                int pos2 = parseInt(r2.getPosition());
                if (pos1 > 0 && pos2 > 0) {
                    if (pos1 < pos2) out.driver1.h2hWins++;
                    else              out.driver2.h2hWins++;
                }
            }
        }
        return out;
    }

    @NonNull
    public static DriverSeason forDriver(@NonNull Season season, @NonNull DriverRef driver,
                                         int lastN) {
        DriverSeason out = new DriverSeason();
        List<FormEntry> form = new ArrayList<>();
        for (Round round : season.getRounds()) {
            if (round.sprint != null) {
                for (RaceResult r : round.sprint) {
                    if (driver.matches(r.getDriver())) {
                        out.stats.points += parseDouble(r.getPoints());
                        break;
                    }
                }
            }
            if (round.race == null) continue;
            for (RaceResult r : round.race) {
                if (!driver.matches(r.getDriver())) continue;
                addRace(out.stats, r);
                form.add(new FormEntry(round.round, round.raceName,
                        parseInt(r.getPosition()), r.getStatus()));
                break;
            }
        }
        int from = Math.max(0, form.size() - Math.max(0, lastN));
        out.lastResults.addAll(form.subList(from, form.size()));
        return out;
    }

    private static void addRace(DriverStats stats, RaceResult r) {
        stats.points += parseDouble(r.getPoints());
        String status = r.getStatus();
        int pos  = parseInt(r.getPosition());
        int grid = parseInt(r.getGridPosition());

        if (pos == 1) stats.wins++;
        if (pos >= 1 && pos <= 3) stats.podiums++;
        if (ResultStatus.isDnf(status)) stats.dnfs++;
        if (ResultStatus.isFinished(status) && pos > 0) {
            stats.finishCount++;
            stats.finishPositionTotal += pos;
        }
        if (grid > 0 && (stats.bestGrid == 0 || grid < stats.bestGrid)) stats.bestGrid = grid;
        if (grid == 1) stats.poles++;
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }
}
