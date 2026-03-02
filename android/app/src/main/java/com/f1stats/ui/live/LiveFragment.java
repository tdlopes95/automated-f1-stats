package com.f1stats.ui.live;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.f1stats.R;
import com.f1stats.models.LiveSession;
import com.f1stats.viewmodels.F1ViewModel;

public class LiveFragment extends Fragment {

    private F1ViewModel viewModel;
    private LiveDriverAdapter adapter;

    private TextView tvSessionStatus, tvSessionName, tvWeather, tvLastUpdated;
    private SwipeRefreshLayout swipeRefresh;

    // Auto-refresh every 15 seconds during live session
    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private static final int REFRESH_INTERVAL_MS = 15000;

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            viewModel.fetchLiveSession();
            autoRefreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_live, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvSessionStatus = view.findViewById(R.id.tv_session_status);
        tvSessionName   = view.findViewById(R.id.tv_session_name);
        tvWeather       = view.findViewById(R.id.tv_weather);
        tvLastUpdated   = view.findViewById(R.id.tv_last_updated);
        swipeRefresh    = view.findViewById(R.id.swipe_refresh_live);

        // RecyclerView setup
        RecyclerView rv = view.findViewById(R.id.rv_live_drivers);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new LiveDriverAdapter();
        rv.setAdapter(adapter);

        viewModel = new ViewModelProvider(requireActivity()).get(F1ViewModel.class);

        // Pull to refresh
        swipeRefresh.setColorSchemeColors(Color.parseColor("#E10600"));
        swipeRefresh.setBackgroundColor(Color.parseColor("#121212"));
        swipeRefresh.setOnRefreshListener(() -> viewModel.fetchLiveSession());

        observeViewModel();
        viewModel.fetchLiveSession();

        // Start auto-refresh
        autoRefreshHandler.postDelayed(autoRefreshRunnable, REFRESH_INTERVAL_MS);
    }

    private void observeViewModel() {
        viewModel.getLiveSession().observe(getViewLifecycleOwner(), this::updateUI);
        viewModel.getLiveLoading().observe(getViewLifecycleOwner(), loading -> {
            swipeRefresh.setRefreshing(loading);
        });
        viewModel.getLiveError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                tvSessionStatus.setText("NO ACTIVE SESSION");
                tvSessionStatus.setBackgroundColor(Color.parseColor("#333333"));
            }
        });
    }

    private void updateUI(LiveSession session) {
        if (session == null) return;

        // Status banner
        tvSessionStatus.setText(session.getSessionStatus());
        try {
            tvSessionStatus.setBackgroundColor(Color.parseColor(session.getStatusColour()));
        } catch (Exception e) {
            tvSessionStatus.setBackgroundColor(Color.parseColor("#39B54A"));
        }

        // Session name
        tvSessionName.setText(session.getSessionName());

        // Weather
        LiveSession.Weather weather = session.getWeather();
        if (weather != null) {
            String weatherStr = "Air " + weather.getDisplayAirTemp() +
                    "  Track " + weather.getDisplayTrackTemp();
            if (weather.isRaining()) weatherStr += "  🌧";
            tvWeather.setText(weatherStr);
        }

        // Last updated
        tvLastUpdated.setText("Last updated: " + session.getLastUpdated());

        // Driver list
        if (session.getDrivers() != null) {
            adapter.setDrivers(session.getDrivers());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }
}