package com.f1stats.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HomeCardParamsTest {

    private static final List<String> AVAILABLE =
            Arrays.asList("Autosport", "Formula 1", "Motorsport.com", "The Race");

    @Test
    public void newsSources_absentMeansAll() {
        assertTrue(HomeCardParams.newsSources(new HomeCardConfig(HomeCardType.NEWS, true)).isEmpty());
    }

    @Test
    public void newsSources_splitTrimsAndDedupes() {
        assertEquals(Arrays.asList("Autosport", "The Race"),
                HomeCardParams.splitSources(" Autosport ,The Race,,Autosport"));
    }

    @Test
    public void joinSources_subsetIsStored_allOrNoneMeansAll() {
        String value = HomeCardParams.joinSources(Arrays.asList("Autosport", "The Race"), AVAILABLE);
        assertEquals("Autosport,The Race", value);
        assertEquals(Arrays.asList("Autosport", "The Race"), HomeCardParams.splitSources(value));
        assertNull(HomeCardParams.joinSources(AVAILABLE, AVAILABLE));
        assertNull(HomeCardParams.joinSources(Collections.emptyList(), AVAILABLE));
    }

    @Test
    public void newCardTypes_needNoChoice() {
        for (HomeCardType type : Arrays.asList(HomeCardType.WEEKEND_WEATHER, HomeCardType.NEWS,
                HomeCardType.ON_THIS_DAY)) {
            assertFalse(type.name(), HomeCardParams.needsChoice(new HomeCardConfig(type, true)));
            assertFalse(type.name(), type.isDefaultEnabled());
        }
        assertTrue(HomeCardType.NEWS.hasOptions());
        assertFalse(HomeCardType.WEEKEND_WEATHER.hasOptions());
        assertFalse(HomeCardType.ON_THIS_DAY.hasOptions());
    }
}
