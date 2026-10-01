package com.f1stats.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HomeLayoutStoreTest {

    /** In-memory stand-in for SharedPreferences. */
    private static class MemoryStorage implements HomeLayoutStore.Storage {
        String json;
        MemoryStorage(String json) { this.json = json; }
        @Override public String read() { return json; }
        @Override public void write(String json) { this.json = json; }
    }

    private static List<HomeCardType> types(List<HomeCardConfig> layout) {
        List<HomeCardType> out = new ArrayList<>();
        for (HomeCardConfig c : layout) out.add(c.getType());
        return out;
    }

    private static HomeCardConfig find(List<HomeCardConfig> layout, HomeCardType type) {
        for (HomeCardConfig c : layout) if (c.getType() == type) return c;
        return null;
    }

    private static final List<HomeCardType> ALL = Arrays.asList(
            HomeCardType.NEXT_RACE, HomeCardType.CHAMPIONSHIP_BATTLE, HomeCardType.LAST_WINNER,
            HomeCardType.FAVOURITE_DRIVER, HomeCardType.FAVOURITE_TEAM, HomeCardType.PINNED_H2H,
            HomeCardType.CHAMPIONSHIP_SNAPSHOT);

    @Test
    public void defaultLayout_originalSectionsEnabled_newCardsDisabled() {
        List<HomeCardConfig> layout = new HomeLayoutStore(new MemoryStorage(null)).getLayout();
        assertEquals(ALL, types(layout));
        for (HomeCardConfig c : layout) {
            boolean original = c.getType().ordinal() <= HomeCardType.LAST_WINNER.ordinal();
            assertEquals(c.getType().name(), original, c.isEnabled());
            assertTrue(c.getParams().isEmpty());
        }
        assertEquals(Arrays.asList(HomeCardType.NEXT_RACE, HomeCardType.CHAMPIONSHIP_BATTLE,
                HomeCardType.LAST_WINNER), new HomeLayoutStore(new MemoryStorage(null)).getEnabledTypes());
    }

    @Test
    public void corruptJson_fallsBackToDefault() {
        HomeLayoutStore store = new HomeLayoutStore(new MemoryStorage("{not json"));
        assertEquals(HomeLayoutStore.defaultLayout(), store.getLayout());
    }

    @Test
    public void roundTrip_keepsOrderEnabledAndParams() {
        MemoryStorage storage = new MemoryStorage(null);
        HomeLayoutStore store = new HomeLayoutStore(storage);

        HomeCardConfig winner = new HomeCardConfig(HomeCardType.LAST_WINNER, true,
                Collections.singletonMap("k", "v"));
        HomeCardConfig snapshot = new HomeCardConfig(HomeCardType.CHAMPIONSHIP_SNAPSHOT, true,
                Collections.singletonMap(HomeCardParams.MODE, HomeCardParams.MODE_CONSTRUCTORS));
        List<HomeCardConfig> saved = Arrays.asList(
                winner,
                new HomeCardConfig(HomeCardType.NEXT_RACE, false),
                snapshot,
                new HomeCardConfig(HomeCardType.CHAMPIONSHIP_BATTLE, true),
                new HomeCardConfig(HomeCardType.FAVOURITE_DRIVER, false),
                new HomeCardConfig(HomeCardType.FAVOURITE_TEAM, false),
                new HomeCardConfig(HomeCardType.PINNED_H2H, false));
        store.save(saved);

        // A fresh store reads only what was persisted
        List<HomeCardConfig> loaded = new HomeLayoutStore(storage).getLayout();
        assertEquals(saved, loaded);
        assertEquals(Arrays.asList(HomeCardType.LAST_WINNER, HomeCardType.CHAMPIONSHIP_SNAPSHOT,
                HomeCardType.CHAMPIONSHIP_BATTLE), new HomeLayoutStore(storage).getEnabledTypes());
        assertTrue(storage.json.contains("\"schemaVersion\":" + HomeLayoutStore.SCHEMA_VERSION));
    }

    @Test
    public void unknownType_isDropped() {
        String json = "{\"schemaVersion\":1,\"cards\":["
                + "{\"type\":\"LAST_WINNER\",\"enabled\":true},"
                + "{\"type\":\"WEATHER_RADAR\",\"enabled\":true},"
                + "{\"type\":\"NEXT_RACE\",\"enabled\":true},"
                + "{\"type\":\"CHAMPIONSHIP_BATTLE\",\"enabled\":false}]}";
        List<HomeCardConfig> layout = new HomeLayoutStore(new MemoryStorage(json)).getLayout();
        assertEquals(Arrays.asList(HomeCardType.LAST_WINNER, HomeCardType.NEXT_RACE,
                HomeCardType.CHAMPIONSHIP_BATTLE), types(layout).subList(0, 3));
        assertEquals(ALL.size(), layout.size());
        assertFalse(find(layout, HomeCardType.CHAMPIONSHIP_BATTLE).isEnabled());
    }

    @Test
    public void d1Layout_getsNewCardsAppendedDisabled() {
        String json = "{\"schemaVersion\":1,\"cards\":["
                + "{\"type\":\"LAST_WINNER\",\"enabled\":true},"
                + "{\"type\":\"NEXT_RACE\",\"enabled\":false},"
                + "{\"type\":\"CHAMPIONSHIP_BATTLE\",\"enabled\":true}]}";
        List<HomeCardConfig> layout = new HomeLayoutStore(new MemoryStorage(json)).getLayout();
        assertEquals(Arrays.asList(HomeCardType.LAST_WINNER, HomeCardType.NEXT_RACE,
                HomeCardType.CHAMPIONSHIP_BATTLE, HomeCardType.FAVOURITE_DRIVER,
                HomeCardType.FAVOURITE_TEAM, HomeCardType.PINNED_H2H,
                HomeCardType.CHAMPIONSHIP_SNAPSHOT), types(layout));
        for (HomeCardConfig c : layout.subList(3, layout.size())) assertFalse(c.isEnabled());
    }

    @Test
    public void missingType_isAppendedDisabled() {
        String json = "{\"schemaVersion\":1,\"cards\":["
                + "{\"type\":\"LAST_WINNER\",\"enabled\":true},"
                + "{\"type\":\"NEXT_RACE\",\"enabled\":true}]}";
        List<HomeCardConfig> layout = new HomeLayoutStore(new MemoryStorage(json)).getLayout();
        assertEquals(Arrays.asList(HomeCardType.LAST_WINNER, HomeCardType.NEXT_RACE,
                HomeCardType.CHAMPIONSHIP_BATTLE), types(layout).subList(0, 3));
        HomeCardConfig appended = find(layout, HomeCardType.CHAMPIONSHIP_BATTLE);
        assertNotNull(appended);
        assertFalse(appended.isEnabled());
    }

    @Test
    public void duplicateType_keepsFirst() {
        String json = "{\"schemaVersion\":1,\"cards\":["
                + "{\"type\":\"NEXT_RACE\",\"enabled\":false},"
                + "{\"type\":\"NEXT_RACE\",\"enabled\":true}]}";
        List<HomeCardConfig> layout = new HomeLayoutStore(new MemoryStorage(json)).getLayout();
        assertEquals(ALL.size(), layout.size());
        assertFalse(find(layout, HomeCardType.NEXT_RACE).isEnabled());
    }

    @Test
    public void save_notifiesListenersAndReset() {
        HomeLayoutStore store = new HomeLayoutStore(new MemoryStorage(null));
        List<List<HomeCardConfig>> received = new ArrayList<>();
        HomeLayoutStore.Listener listener = received::add;
        store.addListener(listener);

        store.save(Collections.singletonList(new HomeCardConfig(HomeCardType.LAST_WINNER, true)));
        assertEquals(1, received.size());
        assertEquals(HomeCardType.LAST_WINNER, received.get(0).get(0).getType());
        assertEquals(ALL.size(), received.get(0).size());

        store.resetToDefault();
        assertEquals(HomeLayoutStore.defaultLayout(), received.get(1));

        store.removeListener(listener);
        store.resetToDefault();
        assertEquals(2, received.size());
    }

    @Test
    public void getLayout_returnsCopies() {
        HomeLayoutStore store = new HomeLayoutStore(new MemoryStorage(null));
        store.getLayout().get(0).setEnabled(false);
        assertTrue(store.getLayout().get(0).isEnabled());
    }

    @Test
    public void welcomeDismissed_persistsAndSurvivesSaveAndReset() {
        MemoryStorage storage = new MemoryStorage(null);
        HomeLayoutStore store = new HomeLayoutStore(storage);
        assertFalse(store.isWelcomeDismissed());

        store.setWelcomeDismissed(true);
        store.save(HomeLayoutStore.defaultLayout());
        store.resetToDefault();
        assertTrue(new HomeLayoutStore(storage).isWelcomeDismissed());
    }

    @Test
    public void favourites_readFromParamsEvenWhenHidden() {
        HomeLayoutStore store = new HomeLayoutStore(new MemoryStorage(null));
        assertEquals(null, store.getFavouriteDriverId());

        HomeCardConfig driver = store.getConfig(HomeCardType.FAVOURITE_DRIVER);
        driver.setParam(HomeCardParams.DRIVER_ID, "russell");
        driver.setParam(HomeCardParams.DRIVER_NAME, "George Russell");
        store.updateConfig(driver);

        assertEquals("russell", store.getFavouriteDriverId());
        assertFalse(store.getConfig(HomeCardType.FAVOURITE_DRIVER).isEnabled());
        // Position kept
        assertEquals(HomeCardType.FAVOURITE_DRIVER, store.getLayout().get(3).getType());
    }

    @Test
    public void needsChoice_rules() {
        HomeCardConfig h2h = new HomeCardConfig(HomeCardType.PINNED_H2H, true);
        assertTrue(HomeCardParams.needsChoice(h2h));
        h2h.setParam(HomeCardParams.TEAMMATES_OF_FAVOURITE, "true");
        assertFalse(HomeCardParams.needsChoice(h2h));
        assertFalse(HomeCardParams.needsChoice(
                new HomeCardConfig(HomeCardType.CHAMPIONSHIP_SNAPSHOT, true)));
        assertTrue(HomeCardParams.needsChoice(new HomeCardConfig(HomeCardType.FAVOURITE_TEAM, true)));
    }

    @Test
    public void resetToDefault_keepsOptions() {
        HomeLayoutStore store = new HomeLayoutStore(new MemoryStorage(null));
        HomeCardConfig team = store.getConfig(HomeCardType.FAVOURITE_TEAM);
        team.setEnabled(true);
        team.setParam(HomeCardParams.CONSTRUCTOR_ID, "mclaren");
        store.updateConfig(team);

        store.resetToDefault();
        HomeCardConfig after = store.getConfig(HomeCardType.FAVOURITE_TEAM);
        assertFalse(after.isEnabled());
        assertEquals("mclaren", store.getFavouriteConstructorId());
    }
}
