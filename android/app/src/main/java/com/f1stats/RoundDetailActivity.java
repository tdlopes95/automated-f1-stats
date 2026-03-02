package com.f1stats;

import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.f1stats.ui.results.PitStopAdapter;
import com.f1stats.ui.results.ResultsAdapter;
import com.f1stats.ui.results.QualifyingAdapter;
import com.f1stats.viewmodels.F1ViewModel;
import com.google.android.material.tabs.TabLayout;


public class RoundDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ROUND        = "extra_round";
    public static final String EXTRA_YEAR         = "extra_year";
    public static final String EXTRA_RACE_NAME    = "extra_race_name";
    public static final String EXTRA_CIRCUIT      = "extra_circuit";

    private F1ViewModel viewModel;
    private ResultsAdapter resultsAdapter;
    private PitStopAdapter pitStopAdapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;

    private int round;
    private int year;

    private static final String[] TABS = {
            "Race", "Q1", "Q2", "Q3", "Sprint", "Pit Stops"
    };
    private String currentTab = "Race";
    private QualifyingAdapter qualifyingAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_round_detail);

        // Get data passed from ResultsFragment
        round    = getIntent().getIntExtra(EXTRA_ROUND, 1);
        year     = getIntent().getIntExtra(EXTRA_YEAR, 2026);
        String raceName = getIntent().getStringExtra(EXTRA_RACE_NAME);
        String circuit  = getIntent().getStringExtra(EXTRA_CIRCUIT);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(raceName);
            getSupportActionBar().setSubtitle(circuit);
        }

        // RecyclerView
        recyclerView  = findViewById(R.id.rv_round_detail);
        swipeRefresh  = findViewById(R.id.swipe_refresh_detail);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        resultsAdapter = new ResultsAdapter();
        pitStopAdapter = new PitStopAdapter();
        qualifyingAdapter = new QualifyingAdapter();
        recyclerView.setAdapter(resultsAdapter);

        swipeRefresh.setColorSchemeColors(Color.parseColor("#E10600"));
        swipeRefresh.setBackgroundColor(Color.parseColor("#121212"));
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
    }

    private void loadCurrentTab() {
        switch (currentTab) {
            case "Race":
                recyclerView.setAdapter(resultsAdapter);
                viewModel.fetchResults(year, round, "Race");
                break;
            case "Q1":
                recyclerView.setAdapter(qualifyingAdapter);
                viewModel.fetchQualifyingResults(year, round);
                viewModel.getQualifyingResults().observe(this, results -> {
                    if (results != null) {
                        qualifyingAdapter.setResults(results,
                                QualifyingAdapter.QualiSession.Q1);
                    }
                    swipeRefresh.setRefreshing(false);
                });
                break;
            case "Q2":
                recyclerView.setAdapter(qualifyingAdapter);
                viewModel.fetchQualifyingResults(year, round);
                viewModel.getQualifyingResults().observe(this, results -> {
                    if (results != null) {
                        qualifyingAdapter.setResults(results,
                                QualifyingAdapter.QualiSession.Q2);
                    }
                    swipeRefresh.setRefreshing(false);
                });
                break;
            case "Q3":
                recyclerView.setAdapter(qualifyingAdapter);
                viewModel.fetchQualifyingResults(year, round);
                viewModel.getQualifyingResults().observe(this, results -> {
                    if (results != null) {
                        qualifyingAdapter.setResults(results,
                                QualifyingAdapter.QualiSession.Q3);
                    }
                    swipeRefresh.setRefreshing(false);
                });
                break;
            case "Sprint":
                recyclerView.setAdapter(resultsAdapter);
                viewModel.fetchResults(year, round, "Sprint");
                break;
            case "Pit Stops":
                recyclerView.setAdapter(pitStopAdapter);
                viewModel.fetchPitStopsForRace(year, round);
                viewModel.getPitStops().removeObservers(this);
                viewModel.getPitStops().observe(this, stops -> {
                    if (stops != null) pitStopAdapter.setPitStops(stops);
                    swipeRefresh.setRefreshing(false);
                });
                break;
        }
    }

    private void observeViewModel() {
        viewModel.getRaceResults().observe(this, results -> {
            swipeRefresh.setRefreshing(false);
            if (results != null) resultsAdapter.setResults(results);
        });
        viewModel.getResultsLoading().observe(this, loading ->
                swipeRefresh.setRefreshing(loading));
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