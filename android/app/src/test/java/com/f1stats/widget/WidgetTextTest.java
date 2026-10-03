package com.f1stats.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;
import java.util.TimeZone;

public class WidgetTextTest {

    // Same templates as strings.xml
    private static final WidgetText.Templates T = new WidgetText.Templates(
            "Choose a favourite driver",
            "P%1$d · %2$s pts",
            "Not in the standings yet",
            "No data yet",
            "Leader",
            "−%1$s to P%2$d",
            "Last race: %1$s · %2$s",
            "No races yet",
            "P%1$d",
            "DNF", "DNS",
            "Next: %1$s · %2$s",
            "No upcoming sessions");

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    // Friday 2 October 2026, 12:00 UTC
    private static final long NOW = 1_790_942_400_000L;
    private static final long HOUR = 60 * 60 * 1000L;

    private static WidgetSnapshot snapshot(int position, double points, double gap) {
        WidgetSnapshot s = new WidgetSnapshot();
        s.driverId = "norris";
        s.name = "Lando Norris";
        s.inStandings = true;
        s.position = position;
        s.points = points;
        s.gapToAhead = gap;
        s.lastRaceName = "Singapore Grand Prix";
        s.lastRacePosition = 2;
        s.lastRaceStatus = "Finished";
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
        assertEquals("P1 · 341 pts", lines.positionPoints);
        assertEquals("Leader", lines.gap);
        assertEquals("Last race: P2 · Singapore Grand Prix", lines.lastRace);
    }

    @Test
    public void gapIsToTheDriverOnePlaceAhead() {
        WidgetText.Lines lines = build(snapshot(3, 287.5, 12), "norris");
        assertEquals("P3 · 287.5 pts", lines.positionPoints);
        assertEquals("−12 to P2", lines.gap);
    }

    @Test
    public void noFavouriteAsksForOne() {
        WidgetText.Lines lines = build(snapshot(1, 341, 0), null);
        assertTrue(lines.chooseDriver);
        assertEquals("Choose a favourite driver", lines.prompt);
        assertEquals("", lines.name);
        assertEquals("", lines.positionPoints);
        assertEquals("", lines.nextSession);

        assertTrue(build(null, "").chooseDriver);
    }

    @Test
    public void snapshotForAnotherDriverIsNotShown() {
        WidgetText.Lines lines = build(snapshot(1, 341, 0), "piastri");
        assertEquals("Lando Norris", lines.name);   // the cached favourite name
        assertEquals("No data yet", lines.positionPoints);
        assertEquals("", lines.gap);
        assertEquals("", lines.lastRace);
    }

    @Test
    public void notInStandings() {
        WidgetSnapshot s = snapshot(0, 0, 0);
        s.inStandings = false;
        s.lastRaceName = null;
        WidgetText.Lines lines = build(s, "norris");
        assertEquals("Not in the standings yet", lines.positionPoints);
        assertEquals("", lines.gap);
        assertEquals("No races yet", lines.lastRace);
    }

    @Test
    public void lastRaceNonFinishes() {
        WidgetSnapshot s = snapshot(4, 200, 5);
        s.lastRacePosition = 18;
        s.lastRaceStatus = "Engine";
        assertEquals("Last race: DNF · Singapore Grand Prix", build(s, "norris").lastRace);
        s.lastRaceStatus = "Did not start";
        assertEquals("Last race: DNS · Singapore Grand Prix", build(s, "norris").lastRace);
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
        assertEquals("No upcoming sessions", build(s, "norris").nextSession);
    }

    @Test
    public void twelveHourClock() {
        WidgetSnapshot s = snapshot(1, 341, 0);
        s.upcoming.add(new WidgetSnapshot.Session("Race", "Japanese Grand Prix", NOW + 24 * HOUR + 3 * HOUR));
        String next = WidgetText.build(T, s, "norris", null, NOW, false, UTC, Locale.US).nextSession;
        assertEquals("Next: Race · Sat 3:00 PM", next);
    }
}
