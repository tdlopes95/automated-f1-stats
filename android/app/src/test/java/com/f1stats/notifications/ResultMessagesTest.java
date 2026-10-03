package com.f1stats.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.f1stats.models.RaceResult;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.util.List;

public class ResultMessagesTest {

    // Same templates as strings.xml
    private static final ResultMessages.Templates T = new ResultMessages.Templates(
            "%1$s finished P%2$s at the %3$s",
            "%1$s finished P%2$s in the %3$s sprint",
            "%1$s retired from the %2$s",
            "%1$s did not start the %2$s",
            "%1$s was disqualified from the %2$s",
            "%1$s sprint",
            " (+%1$s pts)",
            "%1$s: %2$s",
            "%1$s P%2$s",
            "%1$s %2$s",
            "DNF", "DNS", "DSQ");

    private static final String RACE = "Singapore Grand Prix";

    private static String row(String pos, String points, String status, String driverId,
                              String code, String given, String family,
                              String constructorId, String team) {
        return "{\"position\": \"" + pos + "\", \"points\": \"" + points + "\", \"status\": \""
                + status + "\", \"Driver\": {\"driverId\": \"" + driverId + "\", \"code\": \"" + code
                + "\", \"givenName\": \"" + given + "\", \"familyName\": \"" + family
                + "\"}, \"Constructor\": {\"constructorId\": \"" + constructorId
                + "\", \"name\": \"" + team + "\"}}";
    }

    private static final List<RaceResult> RESULTS = new Gson().fromJson("["
            + row("1", "25", "Finished", "norris", "NOR", "Lando", "Norris", "mclaren", "McLaren") + ","
            + row("2", "18", "Finished", "max_verstappen", "VER", "Max", "Verstappen", "red_bull", "Red Bull") + ","
            + row("4", "12", "Finished", "piastri", "PIA", "Oscar", "Piastri", "mclaren", "McLaren") + ","
            + row("11", "0", "+1 Lap", "albon", "ALB", "Alexander", "Albon", "williams", "Williams") + ","
            + row("17", "0", "Retired", "tsunoda", "TSU", "Yuki", "Tsunoda", "red_bull", "Red Bull") + ","
            + row("12", "0.5", "Finished", "sainz", "SAI", "Carlos", "Sainz", "williams", "Williams")
            + "]", new TypeToken<List<RaceResult>>() {}.getType());

    private static String driver(String id, boolean sprint) {
        return ResultMessages.driverLine(T, RESULTS, id, RACE, sprint);
    }

    @Test
    public void winner() {
        assertEquals("Lando Norris finished P1 at the Singapore Grand Prix (+25 pts)",
                driver("norris", false));
    }

    @Test
    public void pointsFinish() {
        assertEquals("Oscar Piastri finished P4 at the Singapore Grand Prix (+12 pts)",
                driver("piastri", false));
    }

    @Test
    public void halfPointsKeepTheirDecimal() {
        assertEquals("Carlos Sainz finished P12 at the Singapore Grand Prix (+0.5 pts)",
                driver("sainz", false));
    }

    @Test
    public void noPoints() {
        assertEquals("Alexander Albon finished P11 at the Singapore Grand Prix",
                driver("albon", false));
    }

    @Test
    public void dnf() {
        assertEquals("Yuki Tsunoda retired from the Singapore Grand Prix", driver("tsunoda", false));
    }

    @Test
    public void sprintWording() {
        assertEquals("Lando Norris finished P1 in the Singapore Grand Prix sprint (+25 pts)",
                driver("norris", true));
        assertEquals("Yuki Tsunoda retired from the Singapore Grand Prix sprint",
                driver("tsunoda", true));
    }

    @Test
    public void unknownOrMissingDriverGivesNoLine() {
        assertNull(driver("hamilton", false));
        assertNull(driver(null, false));
    }

    @Test
    public void teamLineIsInFinishingOrder() {
        assertEquals("McLaren: NOR P1, PIA P4", ResultMessages.teamLine(T, RESULTS, "mclaren"));
        assertEquals("Williams: ALB P11, SAI P12", ResultMessages.teamLine(T, RESULTS, "williams"));
    }

    @Test
    public void teamLineMarksRetirements() {
        assertEquals("Red Bull: VER P2, TSU DNF", ResultMessages.teamLine(T, RESULTS, "red_bull"));
    }

    @Test
    public void bothFavouritesGiveTwoLines() {
        List<String> lines = ResultMessages.lines(T, RESULTS, "max_verstappen", "mclaren", RACE, false);
        assertEquals(List.of(
                "Max Verstappen finished P2 at the Singapore Grand Prix (+18 pts)",
                "McLaren: NOR P1, PIA P4"), lines);
    }

    @Test
    public void noFavouritesGiveNoLines() {
        assertTrue(ResultMessages.lines(T, RESULTS, null, null, RACE, false).isEmpty());
        assertTrue(ResultMessages.lines(T, RESULTS, "hamilton", "ferrari", RACE, false).isEmpty());
    }

    @Test
    public void disqualifiedIsNotRetired() {
        List<RaceResult> dsq = new Gson().fromJson("[" + row("20", "0", "Disqualified",
                "gasly", "GAS", "Pierre", "Gasly", "alpine", "Alpine") + "]",
                new TypeToken<List<RaceResult>>() {}.getType());
        assertEquals("Pierre Gasly was disqualified from the Singapore Grand Prix",
                ResultMessages.driverLine(T, dsq, "gasly", RACE, false));
        assertEquals("Alpine: GAS DSQ", ResultMessages.teamLine(T, dsq, "alpine"));
    }
}
