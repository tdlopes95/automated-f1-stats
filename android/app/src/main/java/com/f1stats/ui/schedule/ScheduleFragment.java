package com.f1stats.ui.schedule;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.widget.TextView;

import com.f1stats.R;
import com.f1stats.viewmodels.F1ViewModel;
import com.f1stats.SeasonHelper;

import java.util.List;
import java.util.Map;

public class ScheduleFragment extends Fragment {

    private F1ViewModel viewModel;
    private ScheduleAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;

    private List<Map<String, Object>> latestSchedule;
    private List<Map<String, Object>> latestMeetings;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefresh = view.findViewById(R.id.swipe_refresh_schedule);
        tvEmpty = view.findViewById(R.id.tv_empty_schedule);
        RecyclerView rv = view.findViewById(R.id.rv_schedule);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ScheduleAdapter();
        rv.setAdapter(adapter);

        viewModel = new ViewModelProvider(requireActivity()).get(F1ViewModel.class);

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.f1_red));
        swipeRefresh.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_dark));
        swipeRefresh.setOnRefreshListener(() -> viewModel.fetchSchedule(SeasonHelper.getCurrentYear()));

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
                    tvEmpty.setText("No races scheduled for " + SeasonHelper.getCurrentYear());
                    tvEmpty.setVisibility(View.VISIBLE);
                }
            }
        });

        viewModel.fetchSchedule(SeasonHelper.getCurrentYear());
        viewModel.fetchMeetings(SeasonHelper.getCurrentYear());
    }

    private void mergeAndUpdate() {
        if (latestSchedule == null) return;
        if (latestMeetings != null) {
            for (Map<String, Object> race : latestSchedule) {
                String raceName = race.get("race_name") != null ?
                        race.get("race_name").toString().toLowerCase() : "";
                for (Map<String, Object> meeting : latestMeetings) {
                    String meetingName = meeting.get("meeting_name") != null ?
                            meeting.get("meeting_name").toString().toLowerCase() : "";
                    if (!meetingName.isEmpty() && (meetingName.equals(raceName)
                            || meetingName.contains(raceName) || raceName.contains(meetingName))) {
                        race.put("circuit_image", meeting.get("circuit_image"));
                        race.put("country_flag", meeting.get("country_flag"));
                        break;
                    }
                }
            }
        }
        adapter.setSchedule(latestSchedule);
        if (latestSchedule.isEmpty()) {
            tvEmpty.setText("No races scheduled for " + SeasonHelper.getCurrentYear());
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }
}