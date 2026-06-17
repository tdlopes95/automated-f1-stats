package com.f1stats;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.f1stats.models.QualifyingResult;
import com.f1stats.models.RaceResult;
import com.f1stats.ui.results.PitStopAdapter;
import com.f1stats.ui.results.ResultsAdapter;
import com.f1stats.ui.results.QualifyingAdapter;
import com.f1stats.ui.strategy.StrategyAdapter;
import com.f1stats.viewmodels.F1ViewModel;
import com.google.android.material.tabs.TabLayout;


public class RoundDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ROUND         = "extra_round";
    public static final String EXTRA_YEAR          = "extra_year";
    public static final String EXTRA_RACE_NAME     = "extra_race_name";
    public static final String EXTRA_CIRCUIT       = "extra_circuit";
    public static final String EXTRA_CIRCUIT_IMAGE = "extra_circuit_image";
    public static final String EXTRA_CIRCUIT_ID    = "extra_circuit_id";
    public static final String EXTRA_COUNTRY_FLAG  = "extra_country_flag";
    public static final String EXTRA_HAS_SPRINT    = "extra_has_sprint";

    private F1ViewModel viewModel;
    private ResultsAdapter resultsAdapter;
    private PitStopAdapter pitStopAdapter;
    private StrategyAdapter strategyAdapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ScrollView svGrid;
    private LinearLayout llGridContainer;
    private LinearLayout llStrategyHeader;
    private TextView tvStrategyEmpty;
    private TextView tvLapTotal;

    private Map<String, Integer> qualiPositionMap = new HashMap<>();

    private int round;
    private int year;

    private List<String> tabNames;
    private String currentTab = "Race";
    private String currentQualiSession = "Q3";
    private QualifyingAdapter qualifyingAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_round_detail);

        round    = getIntent().getIntExtra(EXTRA_ROUND, 1);
        year     = getIntent().getIntExtra(EXTRA_YEAR, 2026);
        String raceName     = getIntent().getStringExtra(EXTRA_RACE_NAME);
        String circuit      = getIntent().getStringExtra(EXTRA_CIRCUIT);
        String circuitImage = getIntent().getStringExtra(EXTRA_CIRCUIT_IMAGE);
        String circuitId    = getIntent().getStringExtra(EXTRA_CIRCUIT_ID);
        String countryFlag  = getIntent().getStringExtra(EXTRA_COUNTRY_FLAG);
        android.util.Log.d("CIRCUIT_DEBUG", "RoundDetailActivity received circuitId=" + circuitId);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(raceName);
            getSupportActionBar().setSubtitle(circuit);
        }

        View llFlagHeader = findViewById(R.id.ll_flag_header);
        ImageView ivCountryFlag = findViewById(R.id.iv_country_flag);
        if (countryFlag != null && !countryFlag.isEmpty()) {
            llFlagHeader.setVisibility(View.VISIBLE);
            Glide.with(this).load(countryFlag).into(ivCountryFlag);
        }

        ImageView ivTrackMap = findViewById(R.id.iv_track_map);
        if (circuitImage != null && !circuitImage.isEmpty()) {
            ivTrackMap.setVisibility(View.VISIBLE);
            Glide.with(this).load(circuitImage).into(ivTrackMap);
            final String finalCircuitImage = circuitImage;
            final String finalCircuit = circuit;
            final String finalCountryFlag = countryFlag;
            final String finalCircuitId = circuitId;
            ivTrackMap.setOnClickListener(v -> {
                Intent intent = new Intent(this, TrackDetailActivity.class);
                intent.putExtra(TrackDetailActivity.EXTRA_CIRCUIT_IMAGE, finalCircuitImage);
                intent.putExtra(TrackDetailActivity.EXTRA_CIRCUIT_NAME, finalCircuit);
                intent.putExtra(TrackDetailActivity.EXTRA_CIRCUIT_ID, finalCircuitId);
                intent.putExtra(TrackDetailActivity.EXTRA_COUNTRY_FLAG, finalCountryFlag);
                android.util.Log.d("CIRCUIT_DEBUG", "RoundDetailActivity passing circuitId=" + finalCircuitId);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });
        }

        recyclerView      = findViewById(R.id.rv_round_detail);
        swipeRefresh      = findViewById(R.id.swipe_refresh_detail);
        svGrid            = findViewById(R.id.sv_grid);
        llGridContainer   = findViewById(R.id.ll_grid_container);
        llStrategyHeader  = findViewById(R.id.ll_strategy_header);
        tvStrategyEmpty   = findViewById(R.id.tv_strategy_empty);
        tvLapTotal        = findViewById(R.id.tv_lap_total);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        resultsAdapter  = new ResultsAdapter();
        pitStopAdapter  = new PitStopAdapter();
        qualifyingAdapter = new QualifyingAdapter();
        strategyAdapter = new StrategyAdapter();
        recyclerView.setAdapter(resultsAdapter);

        resultsAdapter.setOnDriverClickListener(result -> {
            if (result.getDriver() != null) launchDriverProfile(result);
        });
        qualifyingAdapter.setOnDriverClickListener(result -> {
            if (result.getDriver() != null) launchDriverProfile(result);
        });

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.f1_red));
        swipeRefresh.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_dark));
        swipeRefresh.setOnRefreshListener(this::loadCurrentTab);

        setupStrategyLegendDots();

        boolean hasSprint = getIntent().getBooleanExtra(EXTRA_HAS_SPRINT, false);
        tabNames = new ArrayList<>();
        tabNames.add("Race");
        tabNames.add("Q1");
        tabNames.add("Q2");
        tabNames.add("Q3");
        if (hasSprint) tabNames.add("Sprint");
        tabNames.add("Grid");
        tabNames.add("Pit Stops");
        tabNames.add("Strategy");

        TabLayout tabs = findViewById(R.id.tab_layout_detail);
        for (String tab : tabNames) {
            tabs.addTab(tabs.newTab().setText(tab));
        }

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tabNames.get(tab.getPosition());
                recyclerView.animate().alpha(0f).setDuration(100).withEndAction(() -> {
                    loadCurrentTab();
                    recyclerView.animate().alpha(1f).setDuration(150).start();
                }).start();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        viewModel = new ViewModelProvider(this).get(F1ViewModel.class);
        observeViewModel();
        loadCurrentTab();
        viewModel.fetchWeatherForRace(year, round);
    }

    private void setupStrategyLegendDots() {
        setDot(R.id.strategy_dot_soft,   "#FF3333");
        setDot(R.id.strategy_dot_medium, "#FFD700");
        setDot(R.id.strategy_dot_hard,   "#FFFFFF");
        setDot(R.id.strategy_dot_inter,  "#39B54A");
        setDot(R.id.strategy_dot_wet,    "#3399FF");
    }

    private void setDot(int viewId, String hex) {
        View dot = findViewById(viewId);
        if (dot == null) return;
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor(hex));
        dot.setBackground(circle);
    }

    private void loadCurrentTab() {
        tvStrategyEmpty.setVisibility(View.GONE);
        llStrategyHeader.setVisibility(View.GONE);

        if ("Grid".equals(currentTab)) {
            swipeRefresh.setVisibility(View.GONE);
            svGrid.setVisibility(View.VISIBLE);
            viewModel.fetchStartingGridForRace(year, round);
            viewModel.fetchQualifyingResults(year, round);
            return;
        }

        swipeRefresh.setVisibility(View.VISIBLE);
        svGrid.setVisibility(View.GONE);

        switch (currentTab) {
            case "Race":
                recyclerView.setAdapter(resultsAdapter);
                viewModel.fetchResults(year, round, "Race");
                break;
            case "Q1":
                currentQualiSession = "Q1";
                recyclerView.setAdapter(qualifyingAdapter);
                viewModel.fetchQualifyingResults(year, round);
                break;
            case "Q2":
                currentQualiSession = "Q2";
                recyclerView.setAdapter(qualifyingAdapter);
                viewModel.fetchQualifyingResults(year, round);
                break;
            case "Q3":
                currentQualiSession = "Q3";
                recyclerView.setAdapter(qualifyingAdapter);
                viewModel.fetchQualifyingResults(year, round);
                break;
            case "Sprint":
                recyclerView.setAdapter(resultsAdapter);
                viewModel.fetchResults(year, round, "Sprint");
                break;
            case "Pit Stops":
                recyclerView.setAdapter(pitStopAdapter);
                viewModel.fetchPitStopsForRace(year, round);
                break;
            case "Strategy":
                recyclerView.setAdapter(strategyAdapter);
                llStrategyHeader.setVisibility(View.VISIBLE);
                viewModel.fetchResults(year, round, "Race");
                viewModel.fetchStintsForRace(year, round);
                break;
        }
    }

    private void launchDriverProfile(RaceResult result) {
        RaceResult.Driver driver = result.getDriver();
        Intent intent = new Intent(this, DriverProfileActivity.class);
        intent.putExtra(DriverProfileActivity.EXTRA_DRIVER_ID, driver.getDriverId());
        intent.putExtra(DriverProfileActivity.EXTRA_DRIVER_CODE, driver.getCode());
        intent.putExtra(DriverProfileActivity.EXTRA_DRIVER_NAME, driver.getFullName());
        intent.putExtra(DriverProfileActivity.EXTRA_YEAR, year);
        if (result.getConstructor() != null) {
            String team = result.getConstructor().getName();
            intent.putExtra(DriverProfileActivity.EXTRA_TEAM_NAME, team);
            intent.putExtra(DriverProfileActivity.EXTRA_TEAM_COLOUR, getTeamColour(team));
        }
        intent.putExtra(DriverProfileActivity.EXTRA_NATIONALITY, driver.getNationality());
        intent.putExtra(DriverProfileActivity.EXTRA_NUMBER, driver.getNumber());
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private void launchDriverProfile(QualifyingResult result) {
        RaceResult.Driver driver = result.getDriver();
        Intent intent = new Intent(this, DriverProfileActivity.class);
        intent.putExtra(DriverProfileActivity.EXTRA_DRIVER_ID, driver.getDriverId());
        intent.putExtra(DriverProfileActivity.EXTRA_DRIVER_CODE, driver.getCode());
        intent.putExtra(DriverProfileActivity.EXTRA_DRIVER_NAME, driver.getFullName());
        intent.putExtra(DriverProfileActivity.EXTRA_YEAR, year);
        if (result.getConstructor() != null) {
            String team = result.getConstructor().getName();
            intent.putExtra(DriverProfileActivity.EXTRA_TEAM_NAME, team);
            intent.putExtra(DriverProfileActivity.EXTRA_TEAM_COLOUR, getTeamColour(team));
        }
        intent.putExtra(DriverProfileActivity.EXTRA_NATIONALITY, driver.getNationality());
        intent.putExtra(DriverProfileActivity.EXTRA_NUMBER, driver.getNumber());
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    private static String getTeamColour(String teamName) {
        if (teamName == null) return "#FFFFFF";
        java.util.Map<String, String> colours = new java.util.HashMap<>();
        colours.put("Red Bull",     "#3671C6");
        colours.put("Ferrari",      "#E8002D");
        colours.put("Mercedes",     "#27F4D2");
        colours.put("McLaren",      "#FF8000");
        colours.put("Aston Martin", "#229971");
        colours.put("Alpine",       "#FF87BC");
        colours.put("Williams",     "#64C4FF");
        colours.put("RB",           "#6692FF");
        colours.put("Haas",         "#B6BABD");
        colours.put("Audi",         "#B5B5B5");
        colours.put("Kick Sauber",  "#52E252");
        colours.put("Sauber",       "#52E252");
        colours.put("Cadillac",     "#CC0000");
        for (java.util.Map.Entry<String, String> e : colours.entrySet()) {
            if (teamName.contains(e.getKey())) return e.getValue();
        }
        return "#FFFFFF";
    }

    private Map<String, Integer> buildFinishPositions() {
        Map<String, Integer> map = new HashMap<>();
        List<RaceResult> results = viewModel.getRaceResults().getValue();
        if (results != null) {
            for (RaceResult r : results) {
                if (r.getDriver() != null && r.getDriver().getCode() != null) {
                    try {
                        map.put(r.getDriver().getCode(), Integer.parseInt(r.getPosition()));
                    } catch (Exception ignored) {}
                }
            }
        }
        return map;
    }

    private void observeViewModel() {
        viewModel.getWeatherData().observe(this, weather -> {
            if (weather == null) return;
            LinearLayout bar = findViewById(R.id.ll_weather_bar);
            TextView tvAir      = findViewById(R.id.tv_weather_air);
            TextView tvTrack    = findViewById(R.id.tv_weather_track);
            TextView tvHumidity = findViewById(R.id.tv_weather_humidity);
            TextView tvRain     = findViewById(R.id.tv_weather_rain);
            TextView tvWind     = findViewById(R.id.tv_weather_wind);

            Object air      = weather.get("air_temperature");
            Object track    = weather.get("track_temperature");
            Object humidity = weather.get("humidity");
            Object rainfall = weather.get("rainfall");
            Object wind     = weather.get("wind_speed");

            if (air == null && track == null && humidity == null && rainfall == null && wind == null) return;

            if (air != null)      tvAir.setText("🌡️ " + formatNum(air) + "°C");
            if (track != null)    tvTrack.setText("🛣️ " + formatNum(track) + "°C");
            if (humidity != null) tvHumidity.setText("💧 " + formatNum(humidity) + "%");
            if (rainfall != null) {
                double r = toDouble(rainfall);
                tvRain.setText(r > 0 ? "🌧️ Yes" : "🌧️ No");
            }
            if (wind != null)     tvWind.setText("💨 " + formatNum(wind) + " km/h");

            bar.setVisibility(View.VISIBLE);
        });

        viewModel.getRaceResults().observe(this, results -> {
            swipeRefresh.setRefreshing(false);
            if (results != null) resultsAdapter.setResults(results);
        });
        viewModel.getResultsLoading().observe(this, loading ->
                swipeRefresh.setRefreshing(loading));

        viewModel.getQualifyingResults().observe(this, results -> {
            if (results != null) {
                // update qualifying adapter
                QualifyingAdapter.QualiSession session;
                switch (currentQualiSession) {
                    case "Q1": session = QualifyingAdapter.QualiSession.Q1; break;
                    case "Q2": session = QualifyingAdapter.QualiSession.Q2; break;
                    default:   session = QualifyingAdapter.QualiSession.Q3; break;
                }
                qualifyingAdapter.setResults(results, session);

                // build qualiPositionMap for grid position change indicators
                qualiPositionMap = new HashMap<>();
                for (QualifyingResult r : results) {
                    if (r.getDriver() != null && r.getDriver().getCode() != null) {
                        try {
                            qualiPositionMap.put(r.getDriver().getCode(),
                                    Integer.parseInt(r.getPosition()));
                        } catch (Exception ignored) {}
                    }
                }
                // if on Grid tab and grid data already loaded, rebuild
                if ("Grid".equals(currentTab)) {
                    List<Map<String, Object>> grid = viewModel.getStartingGrid().getValue();
                    if (grid != null && !grid.isEmpty()) buildGridView(grid);
                }
            }
            swipeRefresh.setRefreshing(false);
        });

        viewModel.getPitStops().observe(this, stops -> {
            if (stops != null) pitStopAdapter.setPitStops(stops);
            swipeRefresh.setRefreshing(false);
        });
        viewModel.getPitStopsLoading().observe(this, loading ->
                swipeRefresh.setRefreshing(loading));

        viewModel.getStartingGrid().observe(this, grid -> {
            if (grid != null && "Grid".equals(currentTab)) {
                if (grid.isEmpty()) {
                    android.util.Log.e("RoundDetailActivity", "Grid empty for year=" + year + " round=" + round);
                }
                buildGridView(grid);
            }
        });

        viewModel.getStints().observe(this, driverStints -> {
            if (driverStints == null || !"Strategy".equals(currentTab)) return;
            swipeRefresh.setRefreshing(false);
            if (driverStints.isEmpty()) {
                strategyAdapter.setData(driverStints, 0, null);
                tvStrategyEmpty.setVisibility(View.VISIBLE);
                return;
            }
            tvStrategyEmpty.setVisibility(View.GONE);
            int totalLaps = 0;
            for (Map<String, Object> driver : driverStints) {
                Object raw = driver.get("stints");
                if (raw instanceof List) {
                    for (Object s : (List<?>) raw) {
                        if (s instanceof Map) {
                            Object le = ((Map<?, ?>) s).get("lap_end");
                            int lapEnd = le instanceof Number ? ((Number) le).intValue() : 0;
                            if (lapEnd > totalLaps) totalLaps = lapEnd;
                        }
                    }
                }
            }
            if (tvLapTotal != null) tvLapTotal.setText("Lap " + totalLaps);
            strategyAdapter.setData(driverStints, totalLaps, buildFinishPositions());
        });
        viewModel.getStintsLoading().observe(this, loading -> {
            if ("Strategy".equals(currentTab)) swipeRefresh.setRefreshing(loading);
        });
    }

    private void buildGridView(List<Map<String, Object>> grid) {
        llGridContainer.removeAllViews();

        if (grid.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No starting grid data available for this race.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            empty.setPadding(32, 48, 32, 48);
            empty.setGravity(android.view.Gravity.CENTER);
            llGridContainer.addView(empty);
            return;
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels - dpToPx(16);
        int cardWidth = (int) (screenWidth * 0.47);
        int stagger = dpToPx(32);

        for (int i = 0; i < grid.size(); i += 2) {
            Map<String, Object> leftDriver  = grid.get(i);
            Map<String, Object> rightDriver = (i + 1 < grid.size()) ? grid.get(i + 1) : null;

            boolean isAlternate = (i / 2) % 2 == 1;

            RelativeLayout row = new RelativeLayout(this);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dpToPx(4);

            View leftCard = buildGridCard(leftDriver, i + 1, isAlternate);
            int leftId = View.generateViewId();
            leftCard.setId(leftId);
            RelativeLayout.LayoutParams leftParams = new RelativeLayout.LayoutParams(
                    cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            leftParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            row.addView(leftCard, leftParams);

            if (rightDriver != null) {
                View rightCard = buildGridCard(rightDriver, i + 2, isAlternate);
                RelativeLayout.LayoutParams rightParams = new RelativeLayout.LayoutParams(
                        cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                rightParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                rightParams.topMargin = stagger;
                row.addView(rightCard, rightParams);
            }

            llGridContainer.addView(row, rowParams);
        }
    }

    private View buildGridCard(Map<String, Object> driver, int gridPosition, boolean isAlternate) {
        MaterialCardView card = (MaterialCardView) LayoutInflater.from(this)
                .inflate(R.layout.item_grid_position, null);

        String code     = strVal(driver.get("name_acronym"), "???");
        String team     = strVal(driver.get("team_name"), "");
        String colour   = strVal(driver.get("team_colour"), "FFFFFF");
        if (colour != null && !colour.startsWith("#")) colour = "#" + colour;
        String headshot = strVal(driver.get("headshot_url"), null);

        ((TextView) card.findViewById(R.id.tv_grid_position)).setText("P" + gridPosition);
        ((TextView) card.findViewById(R.id.tv_grid_code)).setText(code);
        ((TextView) card.findViewById(R.id.tv_grid_team)).setText(team);

        // alternating card background
        card.setCardBackgroundColor(ContextCompat.getColor(this,
                isAlternate ? R.color.bg_dark : R.color.bg_surface));

        // team colour left border
        try {
            View border = card.findViewById(R.id.view_team_border);
            GradientDrawable bd = new GradientDrawable();
            bd.setColor(Color.parseColor(colour));
            border.setBackground(bd);
        } catch (Exception ignored) {}

        // P1 gold border/glow
        if (gridPosition == 1) {
            card.setStrokeColor(Color.parseColor("#FFD700"));
            card.setStrokeWidth(dpToPx(1));
        }

        // position change vs qualifying
        if (!qualiPositionMap.isEmpty()) {
            Integer qualiPos = qualiPositionMap.get(code);
            if (qualiPos != null) {
                int change = qualiPos - gridPosition;
                TextView tvChange = card.findViewById(R.id.tv_position_change);
                if (change > 0) {
                    tvChange.setText("▲" + change);
                    tvChange.setTextColor(Color.parseColor("#4CAF50"));
                    tvChange.setVisibility(View.VISIBLE);
                } else if (change < 0) {
                    tvChange.setText("▼" + Math.abs(change));
                    tvChange.setTextColor(Color.parseColor("#F44336"));
                    tvChange.setVisibility(View.VISIBLE);
                }
            }
        }

        if (headshot != null && !headshot.isEmpty()) {
            Glide.with(this)
                    .load(headshot)
                    .circleCrop()
                    .into((ImageView) card.findViewById(R.id.iv_grid_headshot));
        }

        return card;
    }

    private static String strVal(Object val, String fallback) {
        return (val != null && !val.toString().isEmpty()) ? val.toString() : fallback;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static String formatNum(Object val) {
        double d = toDouble(val);
        if (d == Math.floor(d)) return String.valueOf((int) d);
        return String.format(java.util.Locale.US, "%.1f", d);
    }

    private static double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0; }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
