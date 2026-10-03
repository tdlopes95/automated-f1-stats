package com.f1stats.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class NextSessionWidgetTest {

    // One race from GET /schedule/{year}, as Retrofit's Gson converter hands it over
    private static final String RACE_RESPONSE = "{\"round\": 19, \"race_name\": \"United States Grand Prix\","
            + " \"circuit\": \"Circuit of the Americas\", \"circuit_id\": \"americas\","
            + " \"country\": \"USA\", \"locality\": \"Austin\", \"date\": \"2026-10-25\","
            + " \"sessions\": ["
            + "{\"name\": \"Practice 1\", \"datetime\": \"2026-10-23T17:30:00Z\"},"
            + "{\"name\": \"Qualifying\", \"datetime\": \"2026-10-24T21:00:00Z\"},"
            + "{\"name\": \"Race\", \"datetime\": \"2026-10-25T20:00:00Z\"}]}";

    /** What F1Repository.saveSchedule writes to CachedSchedule.sessionsJson. */
    private static String storedRow() {
        Gson gson = new Gson();
        Map<String, Object> race = gson.fromJson(RACE_RESPONSE,
                new TypeToken<Map<String, Object>>() {}.getType());
        return gson.toJson(race);
    }

    @Test
    public void parsesTheSessionsOfAStoredRaceRow() {
        List<Map<String, Object>> sessions = NextSessionWidget.parseSessions(storedRow());
        assertEquals(3, sessions.size());
        assertEquals("Practice 1", sessions.get(0).get("name"));
        assertEquals("2026-10-24T21:00:00Z", sessions.get(1).get("datetime"));
        assertEquals("Race", sessions.get(2).get("name"));
    }

    @Test
    public void rowWithoutSessionsIsEmpty() {
        assertTrue(NextSessionWidget.parseSessions(null).isEmpty());
        assertTrue(NextSessionWidget.parseSessions("{\"round\": 1}").isEmpty());
    }

    @Test(expected = JsonParseException.class)
    public void malformedRowThrowsForTheCallerToCatch() {
        NextSessionWidget.parseSessions("[{\"name\": \"Race\"}]");
    }
}
