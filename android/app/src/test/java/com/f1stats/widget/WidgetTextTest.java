package com.f1stats.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class WidgetTextTest {

    // Same templates as strings.xml
    private static final WidgetText.Templates T = new WidgetText.Templates();
    static {
        T.chooseDriver = "Choose a favourite driver";
        T.notInStandings = "Not in the standings yet";
        T.noData = "No data yet";
        T.points = "%1$s pts";
        T.leader = "Leader";
        T.gapToAhead = "−%1$s to P%2$d";
        T.gapToLeader = "−%1$s to leader";
        T.winsPodiums = "%1$s · %2$s";
        T.wins = n -> n + (n == 1 ? " win" : " wins");
        T.podiums = n -> n + (n == 1 ? " podium" : " podiums");
        T.lastResult = "Last: %1$s";
        T.noRaces = "No races yet";
        T.position = "P%1$d";
        T.dnf = "DNF";
        T.dns = "DNS";
        T.dsq = "DSQ";
        T.nextSession = "Next: %1$s · %2$s";
        T.noNextSession = "No upcoming sessions";
        T.h2h = "H2H vs %1$s: %2$d - %3$d";
    }

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    // Friday 2 October 2026, 12:00 UTC
    private static final long NOW = 1_790_942_400_000L;
    private static final long HOUR = 60 * 60 * 1000L;

    private static WidgetSnapshot snapshot(int position, double points, double gap) {
        WidgetSnapshot s = new WidgetSnapshot();
        s.driverId = "norris";
        s.name = "Lando Norris";
        s.code = "NOR";
        s.teamName = "McLaren";
        s.inStandings = true;
        s.position = position;
        s.points = points;
        s.gapToAhead = gap;
        s.lastResults.add(new WidgetSnapshot.Result("Singapore Grand Prix", 2, "Finished"));
        return s;
    }

    private static WidgetText.Lines build(WidgetSnapshot s, String favouriteId) {
        return WidgetText.build(T, s, favouriteId, "Lando Norris", NOW, true, UTC, Locale.UK);
    }

    @Test
    public void leaderShowsLeaderInsteadOfAGap() {
        WidgetText.Lines lines = build(snapshot(1, 341, 0), "norris");
        assertFalse(lines.chooseDriver);
        assertEquals("Lando Norris", lines.name);
        assertEquals("NOR", lines.code);
        assertEquals("McLaren", lines.team);
        assertEquals("P1", lines.position);
        assertEquals("341 pts", lines.points);
        assertEquals("Leader", lines.gap);
        assertEquals("", lines.gapToLeader);
        assertEquals("Last: P2", lines.lastResult);
    }

    @Test
    public void gapIsToTheDriverOnePlaceAhead() {
        WidgetSnapshot s = snapshot(3, 287.5, 12);
        s.gapToLeader = 53.5;
        WidgetText.Lines lines = build(s, "norris");
        assertEquals("P3", lines.position);
        assertEquals("287.5 pts", lines.points);
        assertEquals("−12 to P2", lines.gap);
        assertEquals("−53.5 to leader", lines.gapToLeader);
    }

    @Test
    public void secondPlaceShowsOnlyTheGapAhead() {
        WidgetSnapshot s = snapshot(2, 300, 41);
        s.gapToLeader = 41;
        WidgetText.Lines lines = build(s, "norris");
        assertEquals("−41 to P1", lines.gap);
        assertEquals("", lines.gapToLeader);
    }

    @Test
    public void winsAndPodiumsArePluralised() {
        WidgetSnapshot s = snapshot(1, 341, 0);
        s.wins = 1;
        s.podiums = 7;
        assertEquals("1 win · 7 podiums", build(s, "norris").winsPodiums);
        s.wins = 0;
        s.podiums = 1;
        assertEquals("0 wins · 1 podium", build(s, "norris").winsPodiums);
    }

    @Test
    public void headToHeadAgainstTheTeammate() {
        WidgetSnapshot s = snapshot(2, 300, 10);
        s.teammateCode = "PIA";
        s.h2hWins = 11;
        s.h2hLosses = 6;
        assertEquals("H2H vs PIA: 11 - 6", build(s, "norris").h2h);

        s.teammateCode = null;
        assertEquals("", build(s, "norris").h2h);
    }

    @Test
    public void formChipsKeepTheLastFiveAndClassifyThem() {
        WidgetSnapshot s = snapshot(4, 200, 5);
        s.lastResults.clear();
        s.lastResults.add(new WidgetSnapshot.Result("Bahrain Grand Prix", 1, "Finished"));
        s.lastResults.add(new WidgetSnapshot.Result("Saudi Arabian Grand Prix", 3, "Finished"));
        s.lastResults.add(new WidgetSnapshot.Result("Australian Grand Prix", 7, "Finished"));
        s.lastResults.add(new WidgetSnapshot.Result("Japanese Grand Prix", 14, "+1 Lap"));
        s.lastResults.add(new WidgetSnapshot.Result("Chinese Grand Prix", 18, "Engine"));
        s.lastResults.add(new WidgetSnapshot.Result("Miami Grand Prix", 0, "Disqualified"));

        List<WidgetText.Chip> form = build(s, "norris").form;
        assertEquals(5, form.size());   // the oldest drops off
        assertEquals("P3", form.get(0).label);
        assertEquals(WidgetText.ChipKind.PODIUM, form.get(0).kind);
        assertEquals("P7", form.get(1).label);
        assertEquals(WidgetText.ChipKind.POINTS, form.get(1).kind);
        assertEquals("P14", form.get(2).label);
        assertEquals(WidgetText.ChipKind.FINISH, form.get(2).kind);
        assertEquals("DNF", form.get(3).label);
        assertEquals(WidgetText.ChipKind.RETIRED, form.get(3).kind);
        assertEquals("Chinese Grand Prix", form.get(3).raceName);
        assertEquals("DSQ", form.get(4).label);
        assertEquals(WidgetText.ChipKind.RETIRED, form.get(4).kind);
        assertEquals("Last: DSQ", build(s, "norris").lastResult);
    }

    @Test
    public void noFavouriteAsksForOne() {
        WidgetText.Lines lines = build(snapshot(1, 341, 0), null);
        assertTrue(lines.chooseDriver);
        assertEquals("Choose a favourite driver", lines.prompt);
        assertEquals("", lines.name);
        assertEquals("", lines.points);
        assertEquals("", lines.nextSession);
        assertTrue(lines.form.isEmpty());

        assertTrue(build(null, "").chooseDriver);
    }

    @Test
    public void snapshotForAnotherDriverIsNotShown() {
        WidgetText.Lines lines = build(snapshot(1, 341, 0), "piastri");
        assertEquals("Lando Norris", lines.name);   // the cached favourite name
        assertEquals("Lando Norris", lines.code);
        assertEquals("", lines.position);
        assertEquals("No data yet", lines.points);
        assertEquals("", lines.gap);
        assertEquals("", lines.lastResult);
        assertEquals("", lines.h2h);
        assertTrue(lines.form.isEmpty());
    }

    @Test
    public void notInStandings() {
        WidgetSnapshot s = snapshot(0, 0, 0);
        s.inStandings = false;
        s.lastResults.clear();
        WidgetText.Lines lines = build(s, "norris");
        assertEquals("", lines.position);
        assertEquals("Not in the standings yet", lines.points);
        assertEquals("", lines.gap);
        assertEquals("No races yet", lines.lastResult);
        assertTrue(lines.form.isEmpty());
    }

    @Test
    public void lastRaceNonFinishes() {
        WidgetSnapshot s = snapshot(4, 200, 5);
        s.lastResults.clear();
        s.lastResults.add(new WidgetSnapshot.Result("Singapore Grand Prix", 18, "Engine"));
        assertEquals("Last: DNF", build(s, "norris").lastResult);
        s.lastResults.set(0, new WidgetSnapshot.Result("Singapore Grand Prix", 20, "Did not start"));
        WidgetText.Lines lines = build(s, "norris");
        assertEquals("Last: DNS", lines.lastResult);
        assertEquals(WidgetText.ChipKind.RETIRED, lines.form.get(0).kind);
    }

    @Test
    public void nextSessionSkipsStartedOnesAndAddsTheDateWhenFar() {
        WidgetSnapshot s = snapshot(1, 341, 0);
        s.upcoming.add(new WidgetSnapshot.Session("Practice 1", "Japanese Grand Prix", NOW - HOUR));
        s.upcoming.add(new WidgetSnapshot.Session("Qualifying", "Japanese Grand Prix", NOW + 2 * 24 * HOUR + 2 * HOUR));
        assertEquals("Next: Qualifying · Sun 14:00", build(s, "norris").nextSession);

        s.upcoming.remove(1);
        s.upcoming.add(new WidgetSnapshot.Session("Race", "Qatar Grand Prix", NOW + 10 * 24 * HOUR));
        assertEquals("Next: Race · Mon 12 Oct 12:00", build(s, "norris").nextSession);

        s.upcoming.remove(1);
        WidgetText.Lines none = build(s, "norris");
        assertEquals("No upcoming sessions", none.nextSession);
        assertEquals("", none.nextRaceName);
    }

    @Test
    public void nextRaceShowsTheWeekendsFirstSession() {
        WidgetSnapshot s = snapshot(1, 341, 0);
        // Practice 1 was an hour ago; the race name and weekend start still come from it
        s.upcoming.add(new WidgetSnapshot.Session("Qualifying", "Japanese Grand Prix",
                NOW + 26 * HOUR, NOW - HOUR));
        WidgetText.Lines lines = build(s, "norris");
        assertEquals("Japanese Grand Prix", lines.nextRaceName);
        assertEquals("Fri 2 Oct 11:00", lines.nextRaceDate);

        // Snapshots from before the weekend start was kept fall back to the session itself
        s.upcoming.set(0, new WidgetSnapshot.Session("Race", "Qatar Grand Prix", NOW + 24 * HOUR));
        s.upcoming.get(0).weekendStartMillis = 0;
        assertEquals("Sat 3 Oct 12:00", build(s, "norris").nextRaceDate);
    }

    @Test
    public void twelveHourClock() {
        WidgetSnapshot s = snapshot(1, 341, 0);
        s.upcoming.add(new WidgetSnapshot.Session("Race", "Japanese Grand Prix", NOW + 24 * HOUR + 3 * HOUR));
        String next = WidgetText.build(T, s, "norris", null, NOW, false, UTC, Locale.US).nextSession;
        assertEquals("Next: Race · Sat 3:00 PM", next);
    }
}
