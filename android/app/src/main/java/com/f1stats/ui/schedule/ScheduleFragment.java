package com.f1stats.ui.schedule;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.f1stats.R;
import com.f1stats.viewmodels.F1ViewModel;
import com.f1stats.SeasonHelper;

public class ScheduleFragment extends Fragment {

    private F1ViewModel viewModel;
    private ScheduleAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;

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
        RecyclerView rv = view.findViewById(R.id.rv_schedule);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ScheduleAdapter();
        rv.setAdapter(adapter);

        viewModel = new ViewModelProvider(requireActivity()).get(F1ViewModel.class);

        swipeRefresh.setColorSchemeColors(Color.parseColor("#E10600"));
        swipeRefresh.setBackgroundColor(Color.parseColor("#121212"));
        swipeRefresh.setOnRefreshListener(() -> viewModel.fetchSchedule(SeasonHelper.getCurrentYear()));

        viewModel.getSchedule().observe(getViewLifecycleOwner(), schedule -> {
            swipeRefresh.setRefreshing(false);
            if (schedule != null) adapter.setSchedule(schedule);
        });

        viewModel.getScheduleLoading().observe(getViewLifecycleOwner(), loading ->
                swipeRefresh.setRefreshing(loading));

        viewModel.fetchSchedule(SeasonHelper.getCurrentYear());
    }
}