package com.f1stats.ui.results;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.f1stats.R;
import com.f1stats.RoundDetailActivity;
import com.f1stats.SeasonHelper;
import com.f1stats.models.RoundSchedule;
import com.f1stats.viewmodels.F1ViewModel;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResultsFragment extends Fragment {

    private F1ViewModel viewModel;
    private RoundAdapter roundAdapter;
    private SwipeRefreshLayout swipeRefresh;
    private int selectedYear = SeasonHelper.getCurrentYear();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_results, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefresh = view.findViewById(R.id.swipe_refresh_results);
        RecyclerView recyclerView = view.findViewById(R.id.rv_results);

        // Setup RecyclerView with round list
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        roundAdapter = new RoundAdapter();
        recyclerView.setAdapter(roundAdapter);

        // On round tap → open detail screen
        roundAdapter.setOnRoundClickListener(round -> {
            Intent intent = new Intent(requireContext(), RoundDetailActivity.class);
            intent.putExtra(RoundDetailActivity.EXTRA_ROUND, round.getRound());
            intent.putExtra(RoundDetailActivity.EXTRA_YEAR, selectedYear);
            intent.putExtra(RoundDetailActivity.EXTRA_RACE_NAME, round.getRaceName());
            intent.putExtra(RoundDetailActivity.EXTRA_CIRCUIT, round.getCircuit());
            startActivity(intent);
        });

        // Season selector
        MaterialAutoCompleteTextView spinner =
                view.findViewById(R.id.spinner_season_results); // or spinner_season_standings

        List<String> seasons = SeasonHelper.getAllSeasons();

// Use a non-filtering adapter
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                seasons
        ) {
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = seasons;
                        results.count = seasons.size();
                        return results;
                    }
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }
        };

        spinner.setAdapter(spinnerAdapter);
        spinner.setText(String.valueOf(selectedYear), false);
        spinner.setDropDownHeight(600); // ~5 items

        spinner.setOnItemClickListener((parent, v, position, id) -> {
            selectedYear = Integer.parseInt(seasons.get(position));
            viewModel.fetchSchedule(selectedYear);
        });

        viewModel = new ViewModelProvider(requireActivity()).get(F1ViewModel.class);

        swipeRefresh.setColorSchemeColors(Color.parseColor("#E10600"));
        swipeRefresh.setBackgroundColor(Color.parseColor("#121212"));
        swipeRefresh.setOnRefreshListener(() ->
                viewModel.fetchSchedule(selectedYear));

        // Observe schedule and convert to round list
        viewModel.getSchedule().observe(getViewLifecycleOwner(), schedule -> {
            swipeRefresh.setRefreshing(false);
            if (schedule != null) {
                roundAdapter.setRounds(buildRoundList(schedule));
            }
        });

        viewModel.getScheduleLoading().observe(getViewLifecycleOwner(), loading ->
                swipeRefresh.setRefreshing(loading));

        viewModel.fetchSchedule(selectedYear);
    }

    @SuppressWarnings("unchecked")
    private List<RoundSchedule> buildRoundList(List<Map<String, Object>> schedule) {
        List<RoundSchedule> rounds = new ArrayList<>();
        for (Map<String, Object> race : schedule) {
            int round = race.get("round") != null ?
                    ((Double) race.get("round")).intValue() : 0;
            String raceName = race.get("race_name") != null ?
                    race.get("race_name").toString() : "";
            String circuit = race.get("circuit") != null ?
                    race.get("circuit").toString() : "";
            String country = race.get("country") != null ?
                    race.get("country").toString() : "";

            // Find race date from sessions
            String raceDate = null;
            List<Map<String, Object>> sessions =
                    (List<Map<String, Object>>) race.get("sessions");
            if (sessions != null) {
                for (Map<String, Object> session : sessions) {
                    if ("Race".equals(session.get("name"))) {
                        raceDate = (String) session.get("datetime");
                        break;
                    }
                }
            }

            rounds.add(new RoundSchedule(round, raceName, circuit,
                    country, raceDate, sessions));
        }
        return rounds;
    }
}