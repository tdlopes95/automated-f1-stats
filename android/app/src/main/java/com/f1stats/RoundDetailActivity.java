package com.f1stats;

import android.content.Intent;
import android.graphics.Color;
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

import java.util.List;
import java.util.Map;

import com.f1stats.models.QualifyingResult;
import com.f1stats.models.RaceResult;
import com.f1stats.ui.results.PitStopAdapter;
import com.f1stats.ui.results.ResultsAdapter;
import com.f1stats.ui.results.QualifyingAdapter;
import com.f1stats.viewmodels.F1ViewModel;
import com.google.android.material.tabs.TabLayout;


public class RoundDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ROUND         = "extra_round";
    public static final String EXTRA_YEAR          = "extra_year";
    public static final String EXTRA_RACE_NAME     = "extra_race_name";
    public static final String EXTRA_CIRCUIT       = "extra_circuit";
    public static final String EXTRA_CIRCUIT_IMAGE = "extra_circuit_image";
    public static final String EXTRA_COUNTRY_FLAG  = "extra_country_flag";

    private F1ViewModel viewModel;
    private ResultsAdapter resultsAdapter;
    private PitStopAdapter pitStopAdapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ScrollView svGrid;
    private LinearLayout llGridContainer;

    private int round;
    private int year;

    private static final String[] TABS = {
            "Race", "Q1", "Q2", "Q3", "Sprint", "Grid", "Pit Stops"
    };
    private String currentTab = "Race";
    private String currentQualiSession = "Q3";
    private QualifyingAdapter qualifyingAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_round_detail);

        // Get data passed from ResultsFragment
        round    = getIntent().getIntExtra(EXTRA_ROUND, 1);
        year     = getIntent().getIntExtra(EXTRA_YEAR, 2026);
        String raceName     = getIntent().getStringExtra(EXTRA_RACE_NAME);
        String circuit      = getIntent().getStringExtra(EXTRA_CIRCUIT);
        String circuitImage = getIntent().getStringExtra(EXTRA_CIRCUIT_IMAGE);
        String countryFlag  = getIntent().getStringExtra(EXTRA_COUNTRY_FLAG);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(raceName);
            getSupportActionBar().setSubtitle(circuit);
        }

        // Country flag header
        View llFlagHeader = findViewById(R.id.ll_flag_header);
        ImageView ivCountryFlag = findViewById(R.id.iv_country_flag);
        if (countryFlag != null && !countryFlag.isEmpty()) {
            llFlagHeader.setVisibility(View.VISIBLE);
            Glide.with(this).load(countryFlag).into(ivCountryFlag);
        }

        // Circuit map header
        ImageView ivTrackMap = findViewById(R.id.iv_track_map);
        if (circuitImage != null && !circuitImage.isEmpty()) {
            ivTrackMap.setVisibility(View.VISIBLE);
            Glide.with(this).load(circuitImage).into(ivTrackMap);
        }

        // RecyclerView + grid views
        recyclerView    = findViewById(R.id.rv_round_detail);
        swipeRefresh    = findViewById(R.id.swipe_refresh_detail);
        svGrid          = findViewById(R.id.sv_grid);
        llGridContainer = findViewById(R.id.ll_grid_container);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        resultsAdapter = new ResultsAdapter();
        pitStopAdapter = new PitStopAdapter();
        qualifyingAdapter = new QualifyingAdapter();
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

        // Tabs
        TabLayout tabs = findViewById(R.id.tab_layout_detail);
        for (String tab : TABS) {
            tabs.addTab(tabs.newTab().setText(tab));
        }

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = TABS[tab.getPosition()];
                loadCurrentTab();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        viewModel = new ViewModelProvider(this).get(F1ViewModel.class);
        observeViewModel();
        loadCurrentTab();
        viewModel.fetchWeatherForRace(year, round);
    }

    private void loadCurrentTab() {
        if ("Grid".equals(currentTab)) {
            swipeRefresh.setVisibility(View.GONE);
            svGrid.setVisibility(View.VISIBLE);
            viewModel.fetchStartingGridForRace(year, round);
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
                QualifyingAdapter.QualiSession session;
                switch (currentQualiSession) {
                    case "Q1": session = QualifyingAdapter.QualiSession.Q1; break;
                    case "Q2": session = QualifyingAdapter.QualiSession.Q2; break;
                    default:   session = QualifyingAdapter.QualiSession.Q3; break;
                }
                qualifyingAdapter.setResults(results, session);
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
                buildGridView(grid);
            }
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

        int screenWidth = getResources().getDisplayMetrics().widthPixels
                - dpToPx(16); // account for container padding
        int cardWidth = (int) (screenWidth * 0.47);
        int stagger = dpToPx(20);

        for (int i = 0; i < grid.size(); i += 2) {
            Map<String, Object> leftDriver  = grid.get(i);
            Map<String, Object> rightDriver = (i + 1 < grid.size()) ? grid.get(i + 1) : null;

            RelativeLayout row = new RelativeLayout(this);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dpToPx(4);

            View leftCard = buildGridCard(leftDriver);
            int leftId = View.generateViewId();
            leftCard.setId(leftId);
            RelativeLayout.LayoutParams leftParams = new RelativeLayout.LayoutParams(
                    cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            leftParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            row.addView(leftCard, leftParams);

            if (rightDriver != null) {
                View rightCard = buildGridCard(rightDriver);
                RelativeLayout.LayoutParams rightParams = new RelativeLayout.LayoutParams(
                        cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                rightParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                rightParams.topMargin = stagger;
                row.addView(rightCard, rightParams);
            }

            llGridContainer.addView(row, rowParams);
        }
    }

    private View buildGridCard(Map<String, Object> driver) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_grid_position, null);

        int position = driver.get("position") instanceof Number
                ? ((Number) driver.get("position")).intValue() : 0;
        String code      = strVal(driver.get("name_acronym"), "???");
        String team      = strVal(driver.get("team_name"), "");
        String colour    = strVal(driver.get("team_colour"), "#FFFFFF");
        String headshot  = strVal(driver.get("headshot_url"), null);

        ((TextView) card.findViewById(R.id.tv_grid_position)).setText("P" + position);
        ((TextView) card.findViewById(R.id.tv_grid_code)).setText(code);
        ((TextView) card.findViewById(R.id.tv_grid_team)).setText(team);

        try {
            int base = Color.parseColor(colour);
            card.setBackgroundColor(Color.argb(25,
                    Color.red(base), Color.green(base), Color.blue(base)));
        } catch (Exception ignored) {}

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

    // Handle back button in toolbar
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}