package com.f1stats.ui.home;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.R;
import com.f1stats.home.HomeCardConfig;
import com.f1stats.home.HomeCardParams;
import com.f1stats.home.HomeCardType;
import com.f1stats.models.ConstructorStanding;
import com.f1stats.models.DriverStanding;
import com.f1stats.models.NewsResponse;
import com.f1stats.util.HeadToHead;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the D2 card states from the shared Home data (standings, season results) and the
 * card's params. A null input means that data hasn't arrived; the card then has no data.
 */
final class HomeCardBuilder {

    static final int FORM_RESULTS = 5;
    static final int SNAPSHOT_ROWS = 5;

    private final Context context;
    private final int year;

    HomeCardBuilder(@NonNull Context context, int year) {
        this.context = context;
        this.year = year;
    }

    // ── Favourite driver ──────────────────────────────────────────────────────

    void favouriteDriver(@NonNull HomeCardState.FavouriteDriver state, @NonNull HomeCardConfig config,
                         @Nullable List<DriverStanding> standings,
                         @Nullable HeadToHead.Season season) {
        state.clearPrompt();
        state.year = year;
        String driverId = config.getParam(HomeCardParams.DRIVER_ID);
        if (driverId == null) {
            prompt(state, R.string.home_choose_driver, R.string.home_choose, HomeCardType.FAVOURITE_DRIVER);
            return;
        }
        if (standings == null || season == null) {
            state.hasData = false;
            return;
        }
        int index = indexOfDriver(standings, driverId);
        if (index < 0) {
            notRacing(state, displayName(config.getParam(HomeCardParams.DRIVER_NAME), driverId),
                    R.string.home_change_driver, HomeCardType.FAVOURITE_DRIVER);
            return;
        }

        DriverStanding standing = standings.get(index);
        state.driverId      = driverId;
        state.code          = standing.getDriver().getCode();
        state.name          = standing.getDriver().getFullName();
        state.nationality   = standing.getDriver().getNationality();
        state.number        = standing.getDriver().getNumber();
        state.team          = standing.getTeamName();
        state.constructorId = standing.getConstructorId();
        state.position      = position(standing.getPosition(), index);
        state.points        = parsePoints(standing.getPoints());
        state.gapToAhead    = index > 0
                ? parsePoints(standings.get(index - 1).getPoints()) - state.points : 0;

        HeadToHead.DriverSeason ds = HeadToHead.forDriver(season, HeadToHead.DriverRef.of(driverId),
                FORM_RESULTS);
        state.wins    = ds.stats.wins;
        state.podiums = ds.stats.podiums;
        state.form.clear();
        state.form.addAll(ds.lastResults);
        state.hasData = true;
    }

    // ── Favourite team ────────────────────────────────────────────────────────

    void favouriteTeam(@NonNull HomeCardState.FavouriteTeam state, @NonNull HomeCardConfig config,
                       @Nullable List<ConstructorStanding> constructors,
                       @Nullable List<DriverStanding> drivers,
                       @Nullable HeadToHead.Season season) {
        state.clearPrompt();
        state.year = year;
        String constructorId = config.getParam(HomeCardParams.CONSTRUCTOR_ID);
        if (constructorId == null) {
            prompt(state, R.string.home_choose_team, R.string.home_choose, HomeCardType.FAVOURITE_TEAM);
            return;
        }
        if (constructors == null || drivers == null || season == null) {
            state.hasData = false;
            return;
        }
        int index = -1;
        for (int i = 0; i < constructors.size(); i++) {
            ConstructorStanding c = constructors.get(i);
            if (c.getConstructor() != null && constructorId.equals(c.getConstructor().getConstructorId())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            notRacing(state, displayName(config.getParam(HomeCardParams.TEAM_NAME), constructorId),
                    R.string.home_change_team, HomeCardType.FAVOURITE_TEAM);
            return;
        }

        ConstructorStanding standing = constructors.get(index);
        state.constructorId = constructorId;
        state.name          = standing.getConstructor().getName();
        state.position      = position(standing.getPosition(), index);
        state.points        = parsePoints(standing.getPoints());
        state.gapToAhead    = index > 0
                ? parsePoints(constructors.get(index - 1).getPoints()) - state.points : 0;

        // Standings list each driver under their latest team, highest placed first
        List<DriverStanding> teamDrivers = new ArrayList<>();
        for (DriverStanding d : drivers) {
            if (constructorId.equals(d.getConstructorId()) && d.getDriver() != null) teamDrivers.add(d);
            if (teamDrivers.size() == 2) break;
        }
        state.driverA = teamDrivers.size() > 0 ? row(teamDrivers.get(0)) : null;
        state.driverB = teamDrivers.size() > 1 ? row(teamDrivers.get(1)) : null;
        if (state.driverA != null && state.driverB != null) {
            HeadToHead.Comparison c = HeadToHead.compare(season,
                    HeadToHead.DriverRef.of(state.driverA.driverId),
                    HeadToHead.DriverRef.of(state.driverB.driverId));
            state.h2hA = c.driver1.h2hWins;
            state.h2hB = c.driver2.h2hWins;
        } else {
            state.h2hA = state.h2hB = 0;
        }
        state.hasData = true;
    }

    // ── Pinned head-to-head ───────────────────────────────────────────────────

    void pinnedH2h(@NonNull HomeCardState.PinnedH2h state, @NonNull HomeCardConfig config,
                   @Nullable String favouriteDriverId, @Nullable String favouriteDriverName,
                   @Nullable List<DriverStanding> standings,
                   @Nullable HeadToHead.Season season) {
        state.clearPrompt();
        state.year = year;
        boolean teammates = config.getBooleanParam(HomeCardParams.TEAMMATES_OF_FAVOURITE);
        String id1, id2;
        String name1, name2;

        if (teammates) {
            if (favouriteDriverId == null) {
                prompt(state, R.string.home_choose_favourite_first, R.string.home_choose,
                        HomeCardType.FAVOURITE_DRIVER);
                return;
            }
            if (standings == null || season == null) {
                state.hasData = false;
                return;
            }
            int favIndex = indexOfDriver(standings, favouriteDriverId);
            if (favIndex < 0) {
                notRacing(state, displayName(favouriteDriverName, favouriteDriverId),
                        R.string.home_change_driver, HomeCardType.FAVOURITE_DRIVER);
                return;
            }
            String team = standings.get(favIndex).getConstructorId();
            DriverStanding mate = null;
            for (DriverStanding d : standings) {
                if (d == standings.get(favIndex) || d.getDriver() == null) continue;
                if (team != null && team.equals(d.getConstructorId())) {
                    mate = d;
                    break;
                }
            }
            if (mate == null) {
                state.setPrompt(context.getString(R.string.home_no_teammate,
                        standings.get(favIndex).getDriver().getFullName(), year),
                        context.getString(R.string.home_change), HomeCardType.PINNED_H2H);
                return;
            }
            id1 = favouriteDriverId;
            id2 = mate.getDriver().getDriverId();
            name1 = name2 = null;
        } else {
            id1 = config.getParam(HomeCardParams.DRIVER_ID_1);
            id2 = config.getParam(HomeCardParams.DRIVER_ID_2);
            if (id1 == null || id2 == null) {
                prompt(state, R.string.home_choose_drivers, R.string.home_choose, HomeCardType.PINNED_H2H);
                return;
            }
            if (standings == null || season == null) {
                state.hasData = false;
                return;
            }
            name1 = config.getParam(HomeCardParams.DRIVER_NAME_1);
            name2 = config.getParam(HomeCardParams.DRIVER_NAME_2);
        }

        HeadToHead.Comparison c = HeadToHead.compare(season,
                HeadToHead.DriverRef.of(id1), HeadToHead.DriverRef.of(id2));
        state.driver1 = side(standings, id1, name1, c.driver1);
        state.driver2 = side(standings, id2, name2, c.driver2);
        state.hasData = true;
    }

    private static HomeCardState.PinnedH2h.Side side(List<DriverStanding> standings, String driverId,
                                                     @Nullable String cachedName,
                                                     HeadToHead.DriverStats stats) {
        HomeCardState.PinnedH2h.Side side = new HomeCardState.PinnedH2h.Side();
        side.driverId = driverId;
        side.stats = stats;
        int index = indexOfDriver(standings, driverId);
        if (index >= 0) {
            DriverStanding d = standings.get(index);
            side.name = d.getDriver().getFullName();
            side.team = d.getTeamName();
            side.constructorId = d.getConstructorId();
        } else {
            side.name = displayName(cachedName, driverId);
        }
        return side;
    }

    // ── Championship snapshot ─────────────────────────────────────────────────

    void snapshot(@NonNull HomeCardState.Snapshot state, @NonNull HomeCardConfig config,
                  @Nullable String favouriteDriverId, @Nullable String favouriteConstructorId,
                  @Nullable List<DriverStanding> drivers,
                  @Nullable List<ConstructorStanding> constructors) {
        state.clearPrompt();
        state.year = year;
        state.constructors = HomeCardParams.isConstructorsMode(config);
        state.rows.clear();
        state.favourite = null;

        List<HomeCardState.Snapshot.SnapshotRow> all = new ArrayList<>();
        String favouriteId;
        List<String> ids = new ArrayList<>();
        if (state.constructors) {
            if (constructors == null) {
                state.hasData = false;
                return;
            }
            favouriteId = favouriteConstructorId;
            for (int i = 0; i < constructors.size(); i++) {
                ConstructorStanding c = constructors.get(i);
                if (c.getConstructor() == null) continue;
                HomeCardState.Snapshot.SnapshotRow row = new HomeCardState.Snapshot.SnapshotRow();
                row.position      = position(c.getPosition(), i);
                row.name          = c.getConstructor().getName();
                row.team          = c.getConstructor().getName();
                row.constructorId = c.getConstructor().getConstructorId();
                row.points        = parsePoints(c.getPoints());
                all.add(row);
                ids.add(row.constructorId);
            }
        } else {
            if (drivers == null) {
                state.hasData = false;
                return;
            }
            favouriteId = favouriteDriverId;
            for (int i = 0; i < drivers.size(); i++) {
                DriverStanding d = drivers.get(i);
                if (d.getDriver() == null) continue;
                HomeCardState.Snapshot.SnapshotRow row = new HomeCardState.Snapshot.SnapshotRow();
                row.position      = position(d.getPosition(), i);
                row.name          = d.getDriver().getFullName();
                row.team          = d.getTeamName();
                row.constructorId = d.getConstructorId();
                row.points        = parsePoints(d.getPoints());
                all.add(row);
                ids.add(d.getDriver().getDriverId());
            }
        }

        double leaderPoints = all.isEmpty() ? 0 : all.get(0).points;
        for (int i = 0; i < all.size(); i++) {
            HomeCardState.Snapshot.SnapshotRow row = all.get(i);
            row.gapToLeader = leaderPoints - row.points;
            row.highlighted = favouriteId != null && favouriteId.equals(ids.get(i));
            if (i < SNAPSHOT_ROWS) state.rows.add(row);
            else if (row.highlighted) state.favourite = row;
        }
        state.hasData = true;
    }

    // ── News ──────────────────────────────────────────────────────────────────

    /** The newest headlines from the card's chosen sources (all sources if none chosen). */
    void news(@NonNull HomeCardState.News state, @NonNull HomeCardConfig config,
              @Nullable NewsResponse response) {
        state.items.clear();
        state.noneFromSources = false;
        if (response == null) {
            state.hasData = false;
            return;
        }
        List<String> sources = HomeCardParams.newsSources(config);
        for (NewsResponse.Item item : response.items) {
            if (state.items.size() == HomeCardState.News.MAX_ITEMS) break;
            if (sources.isEmpty() || sources.contains(item.source)) state.items.add(item);
        }
        state.noneFromSources = state.items.isEmpty() && !response.items.isEmpty();
        state.hasData = true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void prompt(HomeCardState state, int messageRes, int actionRes, HomeCardType target) {
        state.setPrompt(context.getString(messageRes), context.getString(actionRes), target);
    }

    private void notRacing(HomeCardState state, String name, int actionRes, HomeCardType target) {
        state.setPrompt(context.getString(R.string.home_not_racing, name, year),
                context.getString(actionRes), target);
    }

    static int indexOfDriver(@NonNull List<DriverStanding> standings, @NonNull String driverId) {
        for (int i = 0; i < standings.size(); i++) {
            DriverStanding d = standings.get(i);
            if (d.getDriver() != null && driverId.equals(d.getDriver().getDriverId())) return i;
        }
        return -1;
    }

    private static HomeCardState.DriverRow row(DriverStanding d) {
        HomeCardState.DriverRow row = new HomeCardState.DriverRow();
        row.driverId      = d.getDriver().getDriverId();
        row.name          = d.getDriver().getFullName();
        row.team          = d.getTeamName();
        row.points        = d.getPoints();
        row.constructorId = d.getConstructorId();
        row.code          = d.getDriver().getCode();
        return row;
    }

    private static String displayName(@Nullable String cachedName, @NonNull String id) {
        return cachedName != null ? cachedName : id;
    }

    private static int position(String position, int index) {
        try {
            return Integer.parseInt(position);
        } catch (Exception e) {
            return index + 1;
        }
    }

    static double parsePoints(String points) {
        if (points == null) return 0;
        try {
            return Double.parseDouble(points.replace(" pts", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** "25" rather than "25.0"; half points kept. */
    static String formatPoints(double points) {
        if (points == Math.rint(points)) return String.valueOf((long) points);
        return String.valueOf(points);
    }
}
