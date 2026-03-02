package com.f1stats.ui.home;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.f1stats.R;
import com.f1stats.viewmodels.F1ViewModel;
import com.f1stats.SeasonHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    private F1ViewModel viewModel;
    private CountDownTimer countDownTimer;

    private TextView tvNextRaceName, tvNextRaceCircuit, tvNextRaceDate;
    private TextView tvCountdown;
    private TextView tvLeaderName, tvLeaderTeam, tvLeaderPoints;
    private TextView tvLastWinner, tvLastRaceName;
    private TextView tvLeaderTitle, tvLeaderGap;

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

        // Bind views
        tvNextRaceName    = view.findViewById(R.id.tv_next_race_name);
        tvNextRaceCircuit = view.findViewById(R.id.tv_next_race_circuit);
        tvNextRaceDate    = view.findViewById(R.id.tv_next_race_date);
        tvCountdown       = view.findViewById(R.id.tv_countdown);
        tvLeaderName      = view.findViewById(R.id.tv_leader_name);
        tvLeaderTeam      = view.findViewById(R.id.tv_leader_team);
        tvLeaderPoints    = view.findViewById(R.id.tv_leader_points);
        tvLastWinner      = view.findViewById(R.id.tv_last_winner);
        tvLastRaceName    = view.findViewById(R.id.tv_last_race_name);
        tvLeaderTitle  = view.findViewById(R.id.tv_leader_title);
        tvLeaderGap    = view.findViewById(R.id.tv_leader_gap);

        viewModel = new ViewModelProvider(requireActivity()).get(F1ViewModel.class);

        observeViewModel();

        // Fetch data
        viewModel.fetchNextRace();
        viewModel.fetchDriverStandings(SeasonHelper.getCurrentYear());
        viewModel.fetchLatestResults("Race", SeasonHelper.getCurrentYear());
    }

    private void observeViewModel() {

        // ── Next race ─────────────────────────────────────────────────────────
        viewModel.getNextRace().observe(getViewLifecycleOwner(), race -> {
            if (race == null) return;

            tvNextRaceName.setText(getString(race, "race_name", "Unknown Race"));
            tvNextRaceCircuit.setText(getString(race, "circuit", ""));

            // Find race session datetime
            List<Map<String, Object>> sessions = (List<Map<String, Object>>) race.get("sessions");
            if (sessions != null) {
                for (Map<String, Object> session : sessions) {
                    if ("Race".equals(session.get("name"))) {
                        String dateStr = (String) session.get("datetime");
                        if (dateStr != null) {
                            tvNextRaceDate.setText(formatDate(dateStr));
                            startCountdown(dateStr);
                        }
                        break;
                    }
                }
            }
        });

        // ── Driver standings → championship leader ────────────────────────────
        viewModel.getDriverStandings().observe(getViewLifecycleOwner(), standings -> {
            if (standings == null || standings.isEmpty()) return;
            var leader = standings.get(0);
            if (leader.getDriver() != null) {
                tvLeaderName.setText(leader.getDriver().getFullName());
            }
            tvLeaderTeam.setText(leader.getTeamName());
            tvLeaderPoints.setText(leader.getPoints() + " pts");

            double gap = leader.getGapToSecond();
            if (gap > 0) {
                tvLeaderGap.setText("(+" + (int) gap + " from P2)");
                tvLeaderGap.setVisibility(View.VISIBLE);
            } else {
                tvLeaderGap.setVisibility(View.GONE);
            }
        });

        // Observe season started to update title label
        viewModel.getSeasonStarted().observe(getViewLifecycleOwner(), started -> {
            if (tvLeaderTitle != null) {
                tvLeaderTitle.setText(started ?
                        "CHAMPIONSHIP LEADER" : "LAST SEASON CHAMPION");
            }
        });

        // ── Latest race results → winner ──────────────────────────────────────
        viewModel.getRaceResults().observe(getViewLifecycleOwner(), results -> {
            if (results == null || results.isEmpty()) return;
            var winner = results.get(0);
            if (winner.getDriver() != null) {
                tvLastWinner.setText(winner.getDriver().getFullName());
            }
            if (winner.getConstructor() != null) {
                tvLastRaceName.setText(winner.getConstructor().getName());
            }
        });

    }

    // ── Countdown timer ───────────────────────────────────────────────────────

    private void startCountdown(String isoDateStr) {
        try {
            // Parse ISO datetime — backend sends e.g. "2026-03-15T15:00:00"
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date raceDate = sdf.parse(isoDateStr);
            if (raceDate == null) return;

            long diff = raceDate.getTime() - System.currentTimeMillis();
            if (diff <= 0) {
                tvCountdown.setText("Race started!");
                return;
            }

            if (countDownTimer != null) countDownTimer.cancel();

            countDownTimer = new CountDownTimer(diff, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    long days    = millisUntilFinished / (1000 * 60 * 60 * 24);
                    long hours   = (millisUntilFinished % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
                    long minutes = (millisUntilFinished % (1000 * 60 * 60)) / (1000 * 60);
                    long seconds = (millisUntilFinished % (1000 * 60)) / 1000;
                    tvCountdown.setText(
                            String.format(Locale.getDefault(), "%dd %02dh %02dm %02ds",
                                    days, hours, minutes, seconds)
                    );
                }

                @Override
                public void onFinish() {
                    tvCountdown.setText("Race started!");
                }
            }.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatDate(String isoDateStr) {
        try {
            SimpleDateFormat input  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            SimpleDateFormat output = new SimpleDateFormat("EEE dd MMM yyyy, HH:mm", Locale.getDefault());
            Date date = input.parse(isoDateStr);
            return date != null ? output.format(date) : isoDateStr;
        } catch (Exception e) {
            return isoDateStr;
        }
    }

    private String getString(Map<String, Object> map, String key, String fallback) {
        Object val = map.get(key);
        return val != null ? val.toString() : fallback;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}