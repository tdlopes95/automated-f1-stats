package com.f1stats.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.f1stats.CompareDriversActivity;
import com.f1stats.CustomizeHomeActivity;
import com.f1stats.DriverProfileActivity;
import com.f1stats.HomeCacheManager;
import com.f1stats.R;
import com.f1stats.SeasonHelper;
import com.f1stats.home.HomeCardConfig;
import com.f1stats.home.HomeCardParams;
import com.f1stats.home.HomeCardType;
import com.f1stats.home.HomeLayoutStore;
import com.f1stats.models.DriverStanding;
import com.f1stats.models.RaceResult;
import com.f1stats.util.DebugLog;
import com.f1stats.util.MeetingMatcher;
import com.f1stats.viewmodels.F1ViewModel;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Home: an ordered list of cards from {@link HomeLayoutStore}. Cards draw on shared data
 * sources (one F1ViewModel LiveData each, current season); a source is fetched once however many
 * cards use it, and each card's loading/error state is derived from the sources it needs, so
 * one failing source never breaks unrelated cards.
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    /** Data Home fetches. Every card needs some subset of these. */
    private enum Source { NEXT_RACE, LATEST_RESULTS, DRIVER_STANDINGS, CONSTRUCTOR_STANDINGS, SEASON_RESULTS }

    private F1ViewModel viewModel;
    private HomeCacheManager cache;
    private HomeLayoutStore layoutStore;
    private HomeCardBuilder builder;
    private int year;

    private final Map<HomeCardType, HomeCardState> states = new EnumMap<>(HomeCardType.class);
    private final Map<Source, HomeCardState.Status> sourceStatus = new EnumMap<>(Source.class);
    private final Map<Source, String> sourceErrors = new EnumMap<>(Source.class);
    /** ensureSeasonResultsCached runs at most once per Home load (plus explicit refreshes). */
    private boolean seasonResultsRequested;

    private List<HomeCardConfig> layout = new ArrayList<>();
    private List<HomeCardConfig> enabledCards = new ArrayList<>();
    private HomeCardAdapter adapter;

    private ShimmerFrameLayout shimmerLayout;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvCards;
    private View layoutError;
    private View layoutEmpty;
    private boolean skeletonShowing;

    private final HomeLayoutStore.Listener layoutListener = newLayout -> applyLayout();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        shimmerLayout = view.findViewById(R.id.shimmer_layout);
        swipeRefresh  = view.findViewById(R.id.swipe_refresh_home);
        rvCards       = view.findViewById(R.id.rv_home_cards);
        layoutError   = view.findViewById(R.id.layout_error);
        layoutEmpty   = view.findViewById(R.id.layout_empty);

        year        = SeasonHelper.getCurrentYear();
        cache       = HomeCacheManager.getInstance(requireContext());
        layoutStore = HomeLayoutStore.getInstance(requireContext());
        viewModel   = new ViewModelProvider(requireActivity()).get(F1ViewModel.class);
        builder     = new HomeCardBuilder(requireContext(), year);

        resetStates();
        adapter = new HomeCardAdapter(states, cardCallbacks);
        rvCards.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCards.setAdapter(adapter);
        // Cards rebind often (countdown state, late headshots); a crossfade on each would flicker
        RecyclerView.ItemAnimator animator = rvCards.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        view.findViewById(R.id.btn_retry).setOnClickListener(v -> {
            layoutError.setVisibility(View.GONE);
            showSkeleton();
            fetch(neededSources(), true);
        });
        view.findViewById(R.id.btn_customize_home).setOnClickListener(v -> openCustomize(null));

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.f1_red));
        swipeRefresh.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_dark));
        swipeRefresh.setOnRefreshListener(this::refreshData);

        readLayout();
        layoutStore.addListener(layoutListener);

        if (cache.hasCache()) {
            loadFromCache();
            showContent();
        } else {
            showSkeleton();
        }
        // Fetch before observing: starting a fetch clears the ViewModel's stale errors
        fetch(neededSources(), false);
        observeViewModel();
        refreshCards();
        updateEmptyState();
    }

    private void resetStates() {
        states.clear();
        states.put(HomeCardType.NEXT_RACE, new HomeCardState.NextRace());
        states.put(HomeCardType.CHAMPIONSHIP_BATTLE, new HomeCardState.Championship());
        states.put(HomeCardType.LAST_WINNER, new HomeCardState.LastWinner());
        states.put(HomeCardType.FAVOURITE_DRIVER, new HomeCardState.FavouriteDriver());
        states.put(HomeCardType.FAVOURITE_TEAM, new HomeCardState.FavouriteTeam());
        states.put(HomeCardType.PINNED_H2H, new HomeCardState.PinnedH2h());
        states.put(HomeCardType.CHAMPIONSHIP_SNAPSHOT, new HomeCardState.Snapshot());
        sourceStatus.clear();
        sourceErrors.clear();
        for (Source source : Source.values()) sourceStatus.put(source, HomeCardState.Status.IDLE);
        seasonResultsRequested = false;
    }

    @SuppressWarnings("unchecked")
    private <S extends HomeCardState> S state(HomeCardType type) {
        return (S) states.get(type);
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private void readLayout() {
        layout = layoutStore.getLayout();
        enabledCards = new ArrayList<>();
        for (HomeCardConfig config : layout) {
            if (config.isEnabled() && config.getType().isAvailable()) enabledCards.add(config);
        }
        adapter.setContent(enabledTypes(), shouldShowWelcome());
    }

    private List<HomeCardType> enabledTypes() {
        List<HomeCardType> types = new ArrayList<>();
        for (HomeCardConfig config : enabledCards) types.add(config.getType());
        return types;
    }

    private boolean shouldShowWelcome() {
        return !layoutStore.isWelcomeDismissed()
                && configFor(HomeCardType.FAVOURITE_DRIVER).getParam(HomeCardParams.DRIVER_ID) == null
                && configFor(HomeCardType.FAVOURITE_TEAM).getParam(HomeCardParams.CONSTRUCTOR_ID) == null;
    }

    private HomeCardConfig configFor(HomeCardType type) {
        for (HomeCardConfig config : layout) {
            if (config.getType() == type) return config;
        }
        return new HomeCardConfig(type, false);
    }

    /** The layout or a card's options changed in Customize. */
    private void applyLayout() {
        if (getView() == null) return;
        readLayout();
        // Only sources never fetched in this Home load; anything loaded is reused
        Set<Source> toFetch = EnumSet.noneOf(Source.class);
        for (Source source : neededSources()) {
            if (sourceStatus.get(source) == HomeCardState.Status.IDLE) toFetch.add(source);
        }
        fetch(toFetch, false);
        refreshCards();
        updateEmptyState();
        checkSettled();
    }

    private void updateEmptyState() {
        if (enabledCards.isEmpty() && !shouldShowWelcome()) {
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
            skeletonShowing = false;
            swipeRefresh.setRefreshing(false);
            swipeRefresh.setVisibility(View.GONE);
            layoutError.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            if (!skeletonShowing && layoutError.getVisibility() != View.VISIBLE) {
                swipeRefresh.setVisibility(View.VISIBLE);
            }
        }
    }

    // ── Sources ───────────────────────────────────────────────────────────────

    /** What a card needs; empty when it can only show a "choose" prompt. */
    private Set<Source> sourcesFor(HomeCardConfig config) {
        if (HomeCardParams.needsChoice(config)) return EnumSet.noneOf(Source.class);
        switch (config.getType()) {
            case NEXT_RACE:
                return EnumSet.of(Source.NEXT_RACE);
            case CHAMPIONSHIP_BATTLE:
                return EnumSet.of(Source.DRIVER_STANDINGS);
            case LAST_WINNER:
                return EnumSet.of(Source.LATEST_RESULTS);
            case FAVOURITE_DRIVER:
                return EnumSet.of(Source.DRIVER_STANDINGS, Source.SEASON_RESULTS);
            case FAVOURITE_TEAM:
                return EnumSet.of(Source.CONSTRUCTOR_STANDINGS, Source.DRIVER_STANDINGS,
                        Source.SEASON_RESULTS);
            case PINNED_H2H:
                if (config.getBooleanParam(HomeCardParams.TEAMMATES_OF_FAVOURITE)
                        && layoutStore.getFavouriteDriverId() == null) {
                    return EnumSet.noneOf(Source.class);
                }
                return EnumSet.of(Source.DRIVER_STANDINGS, Source.SEASON_RESULTS);
            case CHAMPIONSHIP_SNAPSHOT:
                return HomeCardParams.isConstructorsMode(config)
                        ? EnumSet.of(Source.CONSTRUCTOR_STANDINGS)
                        : EnumSet.of(Source.DRIVER_STANDINGS);
            default:
                return EnumSet.noneOf(Source.class);
        }
    }

    private Set<Source> neededSources() {
        Set<Source> sources = EnumSet.noneOf(Source.class);
        for (HomeCardConfig config : enabledCards) sources.addAll(sourcesFor(config));
        return sources;
    }

    /**
     * Starts each source not already loading. Season results are requested at most once per
     * Home load unless {@code userInitiated} (pull-to-refresh, retry).
     */
    private void fetch(Collection<Source> sources, boolean userInitiated) {
        boolean needsHeadshots = false;
        for (Source source : sources) {
            if (sourceStatus.get(source) == HomeCardState.Status.LOADING) continue;
            if (source == Source.SEASON_RESULTS && seasonResultsRequested && !userInitiated) continue;
            sourceStatus.put(source, HomeCardState.Status.LOADING);
            sourceErrors.remove(source);
            switch (source) {
                case NEXT_RACE:
                    viewModel.fetchNextRace();
                    viewModel.fetchMeetings(year);
                    break;
                case LATEST_RESULTS:
                    viewModel.fetchLatestResults("Race", year);
                    needsHeadshots = true;
                    break;
                case DRIVER_STANDINGS:
                    viewModel.fetchHomeDriverStandings(year);
                    needsHeadshots = true;
                    break;
                case CONSTRUCTOR_STANDINGS:
                    viewModel.fetchHomeConstructorStandings(year);
                    break;
                case SEASON_RESULTS:
                    seasonResultsRequested = true;
                    viewModel.fetchHomeSeasonResults(year);
                    break;
            }
        }
        if (needsHeadshots) viewModel.prefetchDrivers(year);
    }

    private void sourceLoaded(Source source) {
        sourceStatus.put(source, HomeCardState.Status.LOADED);
        sourceErrors.remove(source);
        refreshCards();
        checkSettled();
    }

    private void sourceFailed(Source source, String error) {
        DebugLog.d(TAG, source + " failed: " + error);
        sourceStatus.put(source, HomeCardState.Status.ERROR);
        sourceErrors.put(source, error);
        refreshCards();
        checkSettled();
    }

    private void refreshData() {
        layoutError.setVisibility(View.GONE);
        fetch(neededSources(), true);
        refreshCards();
        checkSettled();
    }

    // ── Card state ────────────────────────────────────────────────────────────

    /** Rebuilds the derived cards from the shared data, then every card's status. */
    private void refreshCards() {
        List<DriverStanding> drivers = viewModel.getHomeDriverStandings().getValue();
        String favouriteDriverId = layoutStore.getFavouriteDriverId();
        HomeCardConfig favouriteDriver = configFor(HomeCardType.FAVOURITE_DRIVER);

        builder.favouriteDriver(state(HomeCardType.FAVOURITE_DRIVER), favouriteDriver,
                drivers, viewModel.getHomeSeasonResults().getValue());
        builder.favouriteTeam(state(HomeCardType.FAVOURITE_TEAM), configFor(HomeCardType.FAVOURITE_TEAM),
                viewModel.getHomeConstructorStandings().getValue(), drivers,
                viewModel.getHomeSeasonResults().getValue());
        builder.pinnedH2h(state(HomeCardType.PINNED_H2H), configFor(HomeCardType.PINNED_H2H),
                favouriteDriverId, favouriteDriver.getParam(HomeCardParams.DRIVER_NAME),
                drivers, viewModel.getHomeSeasonResults().getValue());
        builder.snapshot(state(HomeCardType.CHAMPIONSHIP_SNAPSHOT),
                configFor(HomeCardType.CHAMPIONSHIP_SNAPSHOT), favouriteDriverId,
                layoutStore.getFavouriteConstructorId(),
                drivers, viewModel.getHomeConstructorStandings().getValue());

        for (HomeCardConfig config : layout) updateStatus(config);
        adapter.notifyAllCardsChanged();
    }

    private void updateStatus(HomeCardConfig config) {
        HomeCardState state = states.get(config.getType());
        if (state == null) return;
        Set<Source> sources = sourcesFor(config);
        String error = null;
        boolean loading = false;
        boolean allLoaded = true;
        for (Source source : sources) {
            HomeCardState.Status status = sourceStatus.get(source);
            if (status == HomeCardState.Status.ERROR && error == null) error = sourceErrors.get(source);
            if (status == HomeCardState.Status.LOADING) loading = true;
            if (status != HomeCardState.Status.LOADED) allLoaded = false;
        }
        state.error = error;
        if (error != null)   state.status = HomeCardState.Status.ERROR;
        else if (loading)    state.status = HomeCardState.Status.LOADING;
        else if (allLoaded)  state.status = HomeCardState.Status.LOADED;
        else                 state.status = HomeCardState.Status.IDLE;
    }

    /** Once no enabled card is loading: drop the skeleton, end pull-to-refresh. */
    private void checkSettled() {
        if (getView() == null) return;
        if (enabledCards.isEmpty()) {
            swipeRefresh.setRefreshing(false);
            if (skeletonShowing) showContent();
            return;
        }
        boolean anyError = false;
        boolean allFailed = true;
        for (HomeCardConfig config : enabledCards) {
            HomeCardState state = states.get(config.getType());
            if (state == null) continue;
            boolean showsContent = state.hasData || state.promptMessage != null;
            // Includes cached cards being refreshed, so pull-to-refresh ends with the data
            if (state.status == HomeCardState.Status.LOADING) return;
            if (state.status == HomeCardState.Status.ERROR) anyError = true;
            if (showsContent || state.status != HomeCardState.Status.ERROR) allFailed = false;
        }

        boolean wasRefreshing = swipeRefresh.isRefreshing();
        swipeRefresh.setRefreshing(false);
        if (allFailed) {
            // Nothing to show at all
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
            skeletonShowing = false;
            swipeRefresh.setVisibility(View.GONE);
            layoutError.setVisibility(View.VISIBLE);
            return;
        }
        showContent();
        if (wasRefreshing && !anyError) {
            Snackbar.make(requireView(), R.string.home_refresh_ok, Snackbar.LENGTH_SHORT)
                    .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                    .show();
        }
    }

    private void showSkeleton() {
        skeletonShowing = true;
        shimmerLayout.startShimmer();
        shimmerLayout.setVisibility(View.VISIBLE);
        swipeRefresh.setVisibility(View.GONE);
    }

    private void showContent() {
        skeletonShowing = false;
        shimmerLayout.stopShimmer();
        shimmerLayout.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
        swipeRefresh.setVisibility(View.VISIBLE);
    }

    // ── Cache (original three cards) ──────────────────────────────────────────

    private void loadFromCache() {
        HomeCardState.Championship champ = state(HomeCardType.CHAMPIONSHIP_BATTLE);
        String leaderName = cache.loadLeaderName();
        if (leaderName != null) {
            champ.leader = row(leaderName, cache.loadLeaderTeam(), cache.loadLeaderPoints(), null, null);
            champ.seasonStarted = cache.loadSeasonStarted();
            champ.gap = cache.loadLeaderGap();
            String insight = cache.loadLeaderInsight();
            champ.insight = insight != null && !insight.isEmpty() ? insight : insightFor(champ.gap);
            String p2Name = cache.loadP2Name();
            if (p2Name != null) {
                champ.p2 = row(p2Name, cache.loadP2Team(), cache.loadP2Points(), null, null);
            }
            champ.hasData = true;
        }

        String lastWinner = cache.loadLastWinner();
        if (lastWinner != null) {
            HomeCardState.LastWinner winner = state(HomeCardType.LAST_WINNER);
            winner.winner = row(lastWinner, cache.loadLastTeam(), null, null, null);
            winner.raceName = cache.loadLastRaceName();
            winner.hasData = true;
        }

        Map<String, Object> race = cache.loadNextRace();
        if (race != null) {
            HomeCardState.NextRace next = state(HomeCardType.NEXT_RACE);
            next.race = race;
            next.hasData = true;
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private void observeViewModel() {

        viewModel.getNextRace().observe(getViewLifecycleOwner(), race -> {
            HomeCardState.NextRace state = state(HomeCardType.NEXT_RACE);
            state.race = race;
            // No upcoming race: offseason
            state.offseason = race == null;
            state.hasData = true;
            if (race != null) cache.saveNextRace(race);
            updateCircuitImage();
            sourceLoaded(Source.NEXT_RACE);
        });

        // Next race and meetings arrive independently; whichever lands second sets the image
        viewModel.getMeetings().observe(getViewLifecycleOwner(), meetings -> {
            updateCircuitImage();
            adapter.notifyCardChanged(HomeCardType.NEXT_RACE);
        });

        viewModel.getHomeDriverStandings().observe(getViewLifecycleOwner(), standings -> {
            if (standings == null) return;
            if (!standings.isEmpty()) updateChampionship(standings);
            // Empty (no standings yet): the card shows its blank layout rather than a shimmer
            state(HomeCardType.CHAMPIONSHIP_BATTLE).hasData = true;
            sourceLoaded(Source.DRIVER_STANDINGS);
        });

        viewModel.getSeasonStarted().observe(getViewLifecycleOwner(), started -> {
            HomeCardState.Championship state = state(HomeCardType.CHAMPIONSHIP_BATTLE);
            state.seasonStarted = started != null && started;
            if (state.leader != null && state.leader.name != null && !state.leader.name.isEmpty()) {
                cache.saveLeader(state.leader.name, state.leader.team, state.leader.points,
                        state.gap, state.seasonStarted);
            }
            adapter.notifyCardChanged(HomeCardType.CHAMPIONSHIP_BATTLE);
        });

        viewModel.getHomeConstructorStandings().observe(getViewLifecycleOwner(), standings -> {
            if (standings != null) sourceLoaded(Source.CONSTRUCTOR_STANDINGS);
        });

        viewModel.getHomeSeasonResults().observe(getViewLifecycleOwner(), season -> {
            if (season != null) sourceLoaded(Source.SEASON_RESULTS);
        });

        viewModel.getRaceResults().observe(getViewLifecycleOwner(), results -> {
            if (results == null) return;
            HomeCardState.LastWinner state = state(HomeCardType.LAST_WINNER);
            state.hasData = true;
            if (results.isEmpty()) {
                // No race yet this season: blank card, as before
                sourceLoaded(Source.LATEST_RESULTS);
                return;
            }
            RaceResult winner = results.get(0);
            state.winner = row(
                    winner.getDriver() != null ? winner.getDriver().getFullName() : "",
                    winner.getConstructor() != null ? winner.getConstructor().getName() : "",
                    null,
                    winner.getConstructor() != null ? winner.getConstructor().getConstructorId() : null,
                    winner.getDriver() != null ? winner.getDriver().getCode() : null);
            state.hasData = true;
            saveLastWinner(state);
            sourceLoaded(Source.LATEST_RESULTS);
        });

        viewModel.getLastRaceName().observe(getViewLifecycleOwner(), raceName -> {
            if (raceName == null || raceName.isEmpty()) return;
            HomeCardState.LastWinner state = state(HomeCardType.LAST_WINNER);
            state.raceName = raceName;
            saveLastWinner(state);
            adapter.notifyCardChanged(HomeCardType.LAST_WINNER);
        });

        viewModel.getDriverHeadshotMap().observe(getViewLifecycleOwner(), map -> {
            if (map != null) adapter.setHeadshots(map);
        });

        viewModel.getNextRaceError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) sourceFailed(Source.NEXT_RACE, error);
        });
        viewModel.getHomeStandingsError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) sourceFailed(Source.DRIVER_STANDINGS, error);
        });
        viewModel.getLatestResultsError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) sourceFailed(Source.LATEST_RESULTS, error);
        });
        viewModel.getHomeConstructorStandingsError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) sourceFailed(Source.CONSTRUCTOR_STANDINGS, error);
        });
    }

    private void updateChampionship(List<DriverStanding> standings) {
        HomeCardState.Championship state = state(HomeCardType.CHAMPIONSHIP_BATTLE);
        DriverStanding leader = standings.get(0);
        state.leader = row(leader);
        state.gap = leader.getGapToSecond();
        state.insight = insightFor(state.gap);
        Boolean started = viewModel.getSeasonStarted().getValue();
        state.seasonStarted = started != null && started;
        state.p2 = standings.size() >= 2 ? row(standings.get(1)) : null;
        state.hasData = true;

        cache.saveLeader(state.leader.name, state.leader.team, state.leader.points,
                state.gap, state.seasonStarted);
        cache.saveLeaderInsight(state.insight);
        if (state.p2 != null) cache.saveP2(state.p2.name, state.p2.team, state.p2.points);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private final HomeCardAdapter.Callbacks cardCallbacks = new HomeCardAdapter.Callbacks() {
        @Override
        public void onRetry(@NonNull HomeCardType type) {
            Set<Source> failed = EnumSet.noneOf(Source.class);
            for (Source source : sourcesFor(configFor(type))) {
                if (sourceStatus.get(source) != HomeCardState.Status.LOADED) failed.add(source);
            }
            fetch(failed, true);
            refreshCards();
        }

        @Override
        public void onPromptAction(@NonNull HomeCardType target) {
            openCustomize(target);
        }

        @Override
        public void onCardClick(@NonNull HomeCardType type) {
            openCard(type);
        }

        @Override
        public void onWelcomeAction() {
            openCustomize(null);
        }

        @Override
        public void onWelcomeDismiss() {
            layoutStore.setWelcomeDismissed(true);
        }
    };

    private void openCustomize(@Nullable HomeCardType optionsFor) {
        Intent intent = new Intent(requireContext(), CustomizeHomeActivity.class);
        if (optionsFor != null) intent.putExtra(CustomizeHomeActivity.EXTRA_OPEN_OPTIONS, optionsFor.name());
        startActivity(intent);
    }

    private void openCard(HomeCardType type) {
        switch (type) {
            case FAVOURITE_DRIVER:
                openDriverProfile(state(HomeCardType.FAVOURITE_DRIVER));
                break;
            case FAVOURITE_TEAM: {
                HomeCardState.FavouriteTeam team = state(HomeCardType.FAVOURITE_TEAM);
                openCompare(team.driverA != null ? team.driverA.driverId : null,
                        team.driverB != null ? team.driverB.driverId : null);
                break;
            }
            case PINNED_H2H: {
                HomeCardState.PinnedH2h h2h = state(HomeCardType.PINNED_H2H);
                if (h2h.driver1 != null && h2h.driver2 != null) {
                    openCompare(h2h.driver1.driverId, h2h.driver2.driverId);
                }
                break;
            }
            case CHAMPIONSHIP_SNAPSHOT: {
                BottomNavigationView nav = requireActivity().findViewById(R.id.bottom_navigation);
                if (nav != null) nav.setSelectedItemId(R.id.nav_standings);
                break;
            }
            default:
                break;
        }
    }

    private void openDriverProfile(HomeCardState.FavouriteDriver driver) {
        if (driver.driverId == null) return;
        Intent intent = new Intent(requireContext(), DriverProfileActivity.class);
        intent.putExtra(DriverProfileActivity.EXTRA_DRIVER_ID, driver.driverId);
        intent.putExtra(DriverProfileActivity.EXTRA_DRIVER_CODE, driver.code);
        intent.putExtra(DriverProfileActivity.EXTRA_DRIVER_NAME, driver.name);
        intent.putExtra(DriverProfileActivity.EXTRA_YEAR, year);
        intent.putExtra(DriverProfileActivity.EXTRA_TEAM_NAME, driver.team);
        intent.putExtra(DriverProfileActivity.EXTRA_CONSTRUCTOR_ID, driver.constructorId);
        intent.putExtra(DriverProfileActivity.EXTRA_NATIONALITY, driver.nationality);
        intent.putExtra(DriverProfileActivity.EXTRA_NUMBER, driver.number);
        Map<String, String> headshots = viewModel.getDriverHeadshotMap().getValue();
        if (headshots != null && driver.code != null && headshots.get(driver.code) != null) {
            intent.putExtra(DriverProfileActivity.EXTRA_HEADSHOT_URL, headshots.get(driver.code));
        }
        startActivity(intent);
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void openCompare(@Nullable String driverId1, @Nullable String driverId2) {
        Intent intent = new Intent(requireContext(), CompareDriversActivity.class);
        intent.putExtra(CompareDriversActivity.EXTRA_YEAR, year);
        if (driverId1 != null) intent.putExtra(CompareDriversActivity.EXTRA_DRIVER_ID_1, driverId1);
        if (driverId2 != null) intent.putExtra(CompareDriversActivity.EXTRA_DRIVER_ID_2, driverId2);
        startActivity(intent);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveLastWinner(HomeCardState.LastWinner state) {
        if (state.winner == null || state.raceName == null) return;
        cache.saveLastWinner(state.winner.name, state.winner.team, state.raceName);
    }

    private void updateCircuitImage() {
        Map<String, Object> meeting = MeetingMatcher.match(
                viewModel.getNextRace().getValue(), viewModel.getMeetings().getValue());
        if (meeting == null) return;   // keep the placeholder
        Object img = meeting.get("circuit_image");
        if (img != null && !img.toString().isEmpty()) {
            HomeCardState.NextRace state = state(HomeCardType.NEXT_RACE);
            state.circuitImageUrl = img.toString();
        }
    }

    private static HomeCardState.DriverRow row(DriverStanding standing) {
        HomeCardState.DriverRow row = row(
                standing.getDriver() != null ? standing.getDriver().getFullName() : "",
                standing.getTeamName(),
                standing.getPoints(),
                standing.getConstructorId(),
                standing.getDriver() != null ? standing.getDriver().getCode() : null);
        row.driverId = standing.getDriver() != null ? standing.getDriver().getDriverId() : null;
        return row;
    }

    private static HomeCardState.DriverRow row(String name, String team, String points,
                                               String constructorId, String code) {
        HomeCardState.DriverRow row = new HomeCardState.DriverRow();
        row.name = name;
        row.team = team;
        row.points = points;
        row.constructorId = constructorId;
        row.code = code;
        return row;
    }

    private String insightFor(double gap) {
        int g = (int) gap;
        if (g <= 0) return "";
        if (g < 26)  return getString(R.string.home_insight_one_win);
        if (g < 52)  return getString(R.string.home_insight_two_wins);
        if (g < 100) return getString(R.string.home_insight_within_reach);
        return getString(R.string.home_insight_big_gap);
    }

    @Override
    public void onDestroyView() {
        layoutStore.removeListener(layoutListener);
        adapter.releaseAll();
        rvCards.setAdapter(null);
        super.onDestroyView();
    }
}
