package com.f1stats.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class FlagsTest {

    @Test
    public void jolpicaSpellings() {
        assertEquals("https://flagcdn.com/w80/gb.png", Flags.urlFor("UK"));
        assertEquals("https://flagcdn.com/w80/us.png", Flags.urlFor("USA"));
        assertEquals("https://flagcdn.com/w80/ae.png", Flags.urlFor("UAE"));
        assertEquals("https://flagcdn.com/w80/my.png", Flags.urlFor(" Malaysia "));
    }

    @Test
    public void unknownIsNull() {
        assertNull(Flags.urlFor(null));
        assertNull(Flags.urlFor("Atlantis"));
    }

    @Test
    public void meetingFlagWins_elseJolpicaCountry() {
        Map<String, Object> race = new HashMap<>();
        race.put("country", "Malaysia");
        Map<String, Object> meeting = new HashMap<>();
        meeting.put("country_flag", "https://media.formula1.com/bhr.png");

        assertEquals("https://media.formula1.com/bhr.png", MeetingMatcher.flagFor(race, meeting));
        assertEquals("https://flagcdn.com/w80/my.png", MeetingMatcher.flagFor(race, null));
        meeting.put("country_flag", "");
        assertEquals("https://flagcdn.com/w80/my.png", MeetingMatcher.flagFor(race, meeting));
    }
}
