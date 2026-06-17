package com.f1stats.ui.standings;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.f1stats.CompareDriversActivity;
import com.f1stats.DriverHelper;
import com.f1stats.DriverProfileActivity;
import com.f1stats.R;
import com.f1stats.SeasonHelper;
import com.f1stats.SeasonPickerHelper;
import com.f1stats.models.DriverStanding;
import com.f1stats.models.RaceResult;
import com.f1stats.viewmodels.F1ViewModel;
import com.google.android.material.tabs.TabLayout;

import java.util.List;

public class StandingsFragment extends Fragment {

    private F1ViewModel viewModel;
    private StandingsAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;

    private static final String[] TABS = {"Drivers", "Constructors"};
    private String currentTab = "Drivers";
    private int selectedYear = SeasonHelper.getCurrentYear();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_standings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefresh = view.findViewById(R.id.swipe_refresh_standings);
        tvEmpty = view.findViewById(R.id.tv_empty_standings);
        RecyclerView rv = view.findViewById(R.id.rv_standings);
        TabLayout tabs = view.findViewById(R.id.tab_layout_standings);

        ImageButton btnCompare = view.findViewById(R.id.btn_compare_drivers);
        btnCompare.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), CompareDriversActivity.class);
            intent.putExtra(CompareDriversActivity.EXTRA_YEAR, selectedYear);
            startActivity(intent);
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new StandingsAdapter();
        rv.setAdapter(adapter);

        adapter.setOnDriverClickListener(standing -> launchDriverProfile(standing));

        viewModel = new ViewModelProvider(requireActivity()).get(F1ViewModel.class);

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.f1_red));
        swipeRefresh.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_dark));
        swipeRefresh.setOnRefreshListener(this::loadCurrentTab);

        // Tabs
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

        // Season picker
        TextView tvYear = view.findViewById(R.id.tv_selected_year);
        ImageButton btnPrev = view.findViewById(R.id.btn_prev_year);
        ImageButton btnNext = view.findViewById(R.id.btn_next_year);

        tvYear.setText(String.valueOf(selectedYear));
        tvYear.setOnClickListener(v -> SeasonPickerHelper.showPicker(requireContext(), selectedYear,
                year -> {
                    selectedYear = year;
                    tvYear.setText(String.valueOf(selectedYear));
                    loadCurrentTab();
                }));
        btnPrev.setOnClickListener(v -> {
            if (selectedYear > 1950) {
                selectedYear--;
                tvYear.setText(String.valueOf(selectedYear));
                loadCurrentTab();
            }
        });
        btnNext.setOnClickListener(v -> {
            if (selectedYear < SeasonHelper.getCurrentYear()) {
                selectedYear++;
                tvYear.setText(String.valueOf(selectedYear));
                loadCurrentTab();
            }
        });

        observeViewModel();
        loadCurrentTab();
    }

    private void launchDriverProfile(DriverStanding standing) {
        RaceResult.Driver driver = standing.getDriver();
        if (driver == null) return;

        String headshotUrl = null;
        java.util.Map<String, String> headshotMap = viewModel.getDriverHeadshotMap().getValue();
        if (headshotMap != null && driver.getCode() != null) {
            headshotUrl = headshotMap.get(driver.getCode());
        }

        Intent intent = new Intent(requireContext(), DriverProfileActivity.class);
        intent.putExtra(DriverProfileActivity.EXTRA_DRIVER_ID, driver.getDriverId());
        intent.putExtra(DriverProfileActivity.EXTRA_DRIVER_CODE, driver.getCode());
        intent.putExtra(DriverProfileActivity.EXTRA_DRIVER_NAME, driver.getFullName());
        intent.putExtra(DriverProfileActivity.EXTRA_YEAR, selectedYear);
        intent.putExtra(DriverProfileActivity.EXTRA_TEAM_NAME, standing.getTeamName());
        intent.putExtra(DriverProfileActivity.EXTRA_TEAM_COLOUR, getTeamColour(standing.getTeamName()));
        intent.putExtra(DriverProfileActivity.EXTRA_NATIONALITY, driver.getNationality());
        intent.putExtra(DriverProfileActivity.EXTRA_NUMBER, driver.getNumber());
        if (headshotUrl != null) {
            intent.putExtra(DriverProfileActivity.EXTRA_HEADSHOT_URL, headshotUrl);
        }
        startActivity(intent);
        requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
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

    private void loadCurrentTab() {
        viewModel.clearStandings();
        switch (currentTab) {
            case "Drivers":
                viewModel.fetchDriverStandings(selectedYear);
                viewModel.fetchSeasonStats(selectedYear);
                break;
            case "Constructors":
                viewModel.fetchConstructorStandings(selectedYear);
                break;
        }
    }

    private void observeViewModel() {
        viewModel.getDriverStandings().observe(getViewLifecycleOwner(), standings -> {
            swipeRefresh.setRefreshing(false);
            if (standings != null) {
                adapter.setDriverStandings(standings);
                if (standings.isEmpty()) {
                    tvEmpty.setText("Standings not available yet for " + selectedYear);
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                }
            }
        });

        viewModel.getConstructorStandings().observe(getViewLifecycleOwner(), standings -> {
            swipeRefresh.setRefreshing(false);
            if (standings != null) {
                adapter.setConstructorStandings(standings);
                if (standings.isEmpty()) {
                    tvEmpty.setText("Standings not available yet for " + selectedYear);
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                }
            }
        });

        viewModel.getDnfMap().observe(getViewLifecycleOwner(), dnfs -> {
            if (dnfs != null) adapter.setDnfMap(dnfs);
        });

        viewModel.getPodiumMap().observe(getViewLifecycleOwner(), podiums -> {
            if (podiums != null) adapter.setPodiumMap(podiums);
        });

        viewModel.getStandingsLoading().observe(getViewLifecycleOwner(), loading ->
                swipeRefresh.setRefreshing(loading));

        viewModel.getStandingsError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                swipeRefresh.setRefreshing(false);
                List<?> current = "Drivers".equals(currentTab)
                        ? viewModel.getDriverStandings().getValue()
                        : viewModel.getConstructorStandings().getValue();
                if (current == null || current.isEmpty()) {
                    tvEmpty.setText("Standings not available yet for " + selectedYear);
                    tvEmpty.setVisibility(View.VISIBLE);
                }
            }
        });
    }
}