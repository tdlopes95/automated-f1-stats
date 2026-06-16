package com.f1stats.ui.results;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
    private TextView tvEmpty;
    private int selectedYear = SeasonHelper.getCurrentYear();

    private List<Map<String, Object>> latestSchedule;
    private List<Map<String, Object>> latestMeetings;

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
        tvEmpty = view.findViewById(R.id.tv_empty_results);
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
            if (round.getCircuitImage() != null) {
                intent.putExtra(RoundDetailActivity.EXTRA_CIRCUIT_IMAGE, round.getCircuitImage());
            }
            if (round.getCountryFlag() != null) {
                intent.putExtra(RoundDetailActivity.EXTRA_COUNTRY_FLAG, round.getCountryFlag());
            }
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

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.f1_red));
        swipeRefresh.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_dark));
        swipeRefresh.setOnRefreshListener(() ->
                viewModel.fetchSchedule(selectedYear));

        // Observe schedule and convert to round list
        viewModel.getSchedule().observe(getViewLifecycleOwner(), schedule -> {
            swipeRefresh.setRefreshing(false);
            if (schedule != null) {
                latestSchedule = schedule;
                mergeAndUpdate();
            }
        });

        viewModel.getMeetings().observe(getViewLifecycleOwner(), meetings -> {
            if (meetings != null) {
                latestMeetings = meetings;
                mergeAndUpdate();
            }
        });

        viewModel.getScheduleLoading().observe(getViewLifecycleOwner(), loading ->
                swipeRefresh.setRefreshing(loading));

        viewModel.getScheduleError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                swipeRefresh.setRefreshing(false);
                if (latestSchedule == null || latestSchedule.isEmpty()) {
                    tvEmpty.setText("No results available yet for " + selectedYear);
                    tvEmpty.setVisibility(View.VISIBLE);
                }
            }
        });

        viewModel.fetchSchedule(selectedYear);
        viewModel.fetchMeetings(selectedYear);
    }

    private void mergeAndUpdate() {
        if (latestSchedule == null) return;
        List<RoundSchedule> rounds = buildRoundList(latestSchedule);
        if (latestMeetings != null) {
            for (RoundSchedule round : rounds) {
                String raceName = round.getRaceName().toLowerCase();
                for (Map<String, Object> meeting : latestMeetings) {
                    String meetingName = meeting.get("meeting_name") != null ?
                            meeting.get("meeting_name").toString().toLowerCase() : "";
                    if (!meetingName.isEmpty() && (meetingName.equals(raceName)
                            || meetingName.contains(raceName) || raceName.contains(meetingName))) {
                        Object img = meeting.get("circuit_image");
                        if (img != null) round.setCircuitImage(img.toString());
                        Object flag = meeting.get("country_flag");
                        if (flag != null) round.setCountryFlag(flag.toString());
                        break;
                    }
                }
            }
        }
        roundAdapter.setRounds(rounds);
        if (rounds.isEmpty()) {
            tvEmpty.setText("No results available yet for " + selectedYear);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
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