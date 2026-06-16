package com.f1stats.ui.home;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import java.util.Locale;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.f1stats.DateHelper;
import com.f1stats.DriverHelper;
import com.f1stats.HomeCacheManager;
import com.f1stats.R;
import com.f1stats.SeasonHelper;
import com.f1stats.viewmodels.F1ViewModel;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {

    private F1ViewModel viewModel;
    private HomeCacheManager cache;
    private CountDownTimer countDownTimer;

    private TextView tvNextRaceName, tvNextRaceCircuit, tvNextRaceDate, tvNextRaceFlag;
    private TextView tvCountdown;
    private TextView tvLeaderName, tvLeaderTeam, tvLeaderPoints;
    private TextView tvLastWinner, tvLastRaceName, tvLastRaceTeam;
    private TextView tvLeaderTitle, tvLeaderGap;
    private ImageView ivNextRaceCircuit;
    private ImageView ivLeaderHeadshot, ivLastWinnerHeadshot;
    private View viewLeaderColour, viewLastWinnerColour;
    private LinearLayout llSessionTimes;
    private ShimmerFrameLayout shimmerLayout;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutError;

    private String leaderCode = null;
    private String winnerCode = null;

    private boolean nextRaceLoaded   = false;
    private boolean standingsLoaded  = false;
    private boolean lastWinnerLoaded = false;
    private int failCount = 0;

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

        tvNextRaceName    = view.findViewById(R.id.tv_next_race_name);
        tvNextRaceCircuit = view.findViewById(R.id.tv_next_race_circuit);
        tvNextRaceDate    = view.findViewById(R.id.tv_next_race_date);
        tvNextRaceFlag    = view.findViewById(R.id.tv_next_race_flag);
        tvCountdown       = view.findViewById(R.id.tv_countdown);
        tvLeaderName      = view.findViewById(R.id.tv_leader_name);
        tvLeaderTeam      = view.findViewById(R.id.tv_leader_team);
        tvLeaderPoints    = view.findViewById(R.id.tv_leader_points);
        tvLastWinner      = view.findViewById(R.id.tv_last_winner);
        tvLastRaceName    = view.findViewById(R.id.tv_last_race_name);
        tvLastRaceTeam    = view.findViewById(R.id.tv_last_race_team);
        tvLeaderTitle     = view.findViewById(R.id.tv_leader_title);
        tvLeaderGap       = view.findViewById(R.id.tv_leader_gap);
        ivNextRaceCircuit    = view.findViewById(R.id.iv_next_race_circuit);
        ivLeaderHeadshot     = view.findViewById(R.id.iv_leader_headshot);
        ivLastWinnerHeadshot = view.findViewById(R.id.iv_last_winner_headshot);
        viewLeaderColour     = view.findViewById(R.id.view_leader_colour);
        viewLastWinnerColour = view.findViewById(R.id.view_last_winner_colour);
        llSessionTimes    = view.findViewById(R.id.ll_session_times);
        shimmerLayout     = view.findViewById(R.id.shimmer_layout);
        swipeRefresh      = view.findViewById(R.id.swipe_refresh_home);
        layoutError       = view.findViewById(R.id.layout_error);

        cache = HomeCacheManager.getInstance(requireContext());

        view.findViewById(R.id.btn_retry).setOnClickListener(v -> {
            layoutError.setVisibility(View.GONE);
            failCount        = 0;
            nextRaceLoaded   = false;
            standingsLoaded  = false;
            lastWinnerLoaded = false;
            showSkeleton();
            fetchData();
        });

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.f1_red));
        swipeRefresh.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_dark));
        swipeRefresh.setOnRefreshListener(this::refreshData);

        viewModel = new ViewModelProvider(requireActivity()).get(F1ViewModel.class);
        observeViewModel();

        if (cache.hasCache()) {
            loadFromCache();
            showContent();
            fetchData();
        } else {
            showSkeleton();
            fetchData();
        }
    }

    // ── Cache ─────────────────────────────────────────────────────────────────

    private void loadFromCache() {
        tvLeaderName.setText(cache.loadLeaderName());
        tvLeaderTeam.setText(cache.loadLeaderTeam());
        tvLeaderPoints.setText(cache.loadLeaderPoints());
        tvLeaderTitle.setText(cache.loadSeasonStarted() ?
                "CHAMPIONSHIP LEADER" : "LAST SEASON CHAMPION");
        float gap = cache.loadLeaderGap();
        if (gap > 0) {
            tvLeaderGap.setText("(+" + (int) gap + " from P2)");
            tvLeaderGap.setVisibility(View.VISIBLE);
        } else {
            tvLeaderGap.setVisibility(View.GONE);
        }

        tvLastWinner.setText(cache.loadLastWinner());
        tvLastRaceTeam.setText(cache.loadLastTeam());
        tvLastRaceName.setText(cache.loadLastRaceName());

        Map<String, Object> race = cache.loadNextRace();
        if (race != null) {
            tvNextRaceName.setText(getString(race, "race_name", ""));
            tvNextRaceCircuit.setText(getString(race, "circuit", ""));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sessions =
                    (List<Map<String, Object>>) race.get("sessions");
            if (sessions != null) {
                buildSessionTimes(sessions);
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
        }
    }

    private void showContent() {
        shimmerLayout.stopShimmer();
        shimmerLayout.setVisibility(View.GONE);
        swipeRefresh.setVisibility(View.VISIBLE);
        nextRaceLoaded   = true;
        standingsLoaded  = true;
        lastWinnerLoaded = true;
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private void fetchData() {
        viewModel.fetchNextRace();
        viewModel.fetchDriverStandings(SeasonHelper.getCurrentYear());
        viewModel.fetchLatestResults("Race", SeasonHelper.getCurrentYear());
        viewModel.fetchMeetings(SeasonHelper.getCurrentYear());
        viewModel.prefetchDrivers(SeasonHelper.getCurrentYear());
    }

    private void refreshData() {
        failCount        = 0;
        nextRaceLoaded   = false;
        standingsLoaded  = false;
        lastWinnerLoaded = false;
        layoutError.setVisibility(View.GONE);
        fetchData();
    }

    private void showSkeleton() {
        shimmerLayout.startShimmer();
        shimmerLayout.setVisibility(View.VISIBLE);
        swipeRefresh.setVisibility(View.GONE);
    }

    private void checkAllLoaded() {
        if (nextRaceLoaded && standingsLoaded && lastWinnerLoaded) {
            shimmerLayout.stopShimmer();
            shimmerLayout.setVisibility(View.GONE);
            swipeRefresh.setVisibility(View.VISIBLE);
            boolean wasRefreshing = swipeRefresh.isRefreshing();
            swipeRefresh.setRefreshing(false);
            if (wasRefreshing) {
                Snackbar.make(requireView(), "Refresh OK", Snackbar.LENGTH_SHORT)
                        .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                        .show();
            }
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private void observeViewModel() {

        viewModel.getNextRace().observe(getViewLifecycleOwner(), race -> {
            if (race == null) return;
            tvNextRaceName.setText(getString(race, "race_name", "Unknown Race"));
            tvNextRaceCircuit.setText(getString(race, "circuit", ""));
            tvNextRaceFlag.setText(DriverHelper.getFlagForCountry(getString(race, "country", "")));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sessions =
                    (List<Map<String, Object>>) race.get("sessions");
            if (sessions != null) {
                buildSessionTimes(sessions);
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
            cache.saveNextRace(race);
            nextRaceLoaded = true;
            checkAllLoaded();
        });

        viewModel.getDriverStandings().observe(getViewLifecycleOwner(), standings -> {
            if (standings == null || standings.isEmpty()) return;
            var leader = standings.get(0);
            String name   = leader.getDriver() != null ? leader.getDriver().getFullName() : "";
            String team   = leader.getTeamName();
            String points = leader.getPoints() + " pts";
            double gap    = leader.getGapToSecond();

            tvLeaderName.setText(name);
            tvLeaderTeam.setText(team);
            tvLeaderPoints.setText(points);
            if (gap > 0) {
                tvLeaderGap.setText("(+" + (int) gap + " from P2)");
                tvLeaderGap.setVisibility(View.VISIBLE);
            } else {
                tvLeaderGap.setVisibility(View.GONE);
            }

            applyTeamColour(viewLeaderColour, team);
            leaderCode = leader.getDriver() != null ? leader.getDriver().getCode() : null;
            loadHeadshot(ivLeaderHeadshot, leaderCode,
                    viewModel.getDriverHeadshotMap().getValue());

            standingsLoaded = true;
            checkAllLoaded();
        });

        viewModel.getSeasonStarted().observe(getViewLifecycleOwner(), started -> {
            if (tvLeaderTitle != null) {
                tvLeaderTitle.setText(started != null && started ?
                        "CHAMPIONSHIP LEADER" : "LAST SEASON CHAMPION");
            }
            String name   = tvLeaderName.getText() != null ? tvLeaderName.getText().toString() : "";
            String team   = tvLeaderTeam.getText() != null ? tvLeaderTeam.getText().toString() : "";
            String points = tvLeaderPoints.getText() != null ? tvLeaderPoints.getText().toString() : "";
            if (!name.isEmpty()) {
                cache.saveLeader(name, team, points, cache.loadLeaderGap(),
                        started != null && started);
            }
        });

        viewModel.getRaceResults().observe(getViewLifecycleOwner(), results -> {
            if (results == null || results.isEmpty()) return;
            var winner = results.get(0);
            String winnerName = winner.getDriver() != null ?
                    winner.getDriver().getFullName() : "";
            String team = winner.getConstructor() != null ?
                    winner.getConstructor().getName() : "";
            tvLastWinner.setText(winnerName);
            tvLastRaceTeam.setText(team);

            applyTeamColour(viewLastWinnerColour, team);
            winnerCode = winner.getDriver() != null ? winner.getDriver().getCode() : null;
            loadHeadshot(ivLastWinnerHeadshot, winnerCode,
                    viewModel.getDriverHeadshotMap().getValue());

            lastWinnerLoaded = true;
            checkAllLoaded();
        });

        viewModel.getLastRaceName().observe(getViewLifecycleOwner(), raceName -> {
            if (raceName != null && !raceName.isEmpty()) {
                tvLastRaceName.setText(raceName);
                String winnerName = tvLastWinner.getText() != null ?
                        tvLastWinner.getText().toString() : "";
                String team = tvLastRaceTeam.getText() != null ?
                        tvLastRaceTeam.getText().toString() : "";
                cache.saveLastWinner(winnerName, team, raceName);
            }
        });

        viewModel.getMeetings().observe(getViewLifecycleOwner(), meetings -> {
            if (meetings == null || ivNextRaceCircuit == null) return;
            String nextRaceName = tvNextRaceName.getText() != null ?
                    tvNextRaceName.getText().toString().toLowerCase() : "";
            if (nextRaceName.isEmpty()) return;
            for (Map<String, Object> meeting : meetings) {
                String meetingName = meeting.get("meeting_name") != null ?
                        meeting.get("meeting_name").toString().toLowerCase() : "";
                if (!meetingName.isEmpty() && (meetingName.equals(nextRaceName)
                        || meetingName.contains(nextRaceName) || nextRaceName.contains(meetingName))) {
                    Object img = meeting.get("circuit_image");
                    if (img != null && !img.toString().isEmpty()) {
                        Glide.with(requireContext()).load(img.toString()).into(ivNextRaceCircuit);
                    }
                    break;
                }
            }
        });

        viewModel.getDriverHeadshotMap().observe(getViewLifecycleOwner(), map -> {
            if (map == null) return;
            loadHeadshot(ivLeaderHeadshot, leaderCode, map);
            loadHeadshot(ivLastWinnerHeadshot, winnerCode, map);
        });

        viewModel.getHomeError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                failCount++;
                swipeRefresh.setRefreshing(false);
                if (failCount >= 3) {
                    if (!cache.hasCache()) {
                        shimmerLayout.stopShimmer();
                        shimmerLayout.setVisibility(View.GONE);
                        swipeRefresh.setVisibility(View.GONE);
                        layoutError.setVisibility(View.VISIBLE);
                    }
                    failCount = 0;
                }
                viewModel.clearHomeError();
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyTeamColour(View strip, String teamName) {
        if (strip == null || teamName == null) return;
        Map<String, String> colours = new java.util.HashMap<>();
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
        String hex = "#FFFFFF";
        for (Map.Entry<String, String> entry : colours.entrySet()) {
            if (teamName.contains(entry.getKey())) {
                hex = entry.getValue();
                break;
            }
        }
        try {
            strip.setBackgroundColor(Color.parseColor(hex));
        } catch (Exception ignored) {}
    }

    private void loadHeadshot(ImageView iv, String code, Map<String, String> headshotMap) {
        if (iv == null || code == null || headshotMap == null) return;
        String url = headshotMap.get(code);
        if (url != null && !url.isEmpty()) {
            Glide.with(requireContext())
                    .load(url)
                    .circleCrop()
                    .into(iv);
        }
    }

    private void buildSessionTimes(List<Map<String, Object>> sessions) {
        if (llSessionTimes == null || sessions == null || getContext() == null) return;
        llSessionTimes.removeAllViews();
        float density = requireContext().getResources().getDisplayMetrics().density;
        int topMarginPx = Math.round(4 * density);
        int colorHint = ContextCompat.getColor(requireContext(), R.color.text_hint);
        int colorSecondary = ContextCompat.getColor(requireContext(), R.color.text_secondary);

        for (Map<String, Object> session : sessions) {
            String name    = (String) session.get("name");
            String dateStr = (String) session.get("datetime");
            if (name == null || dateStr == null) continue;

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = topMarginPx;
            row.setLayoutParams(rowParams);

            TextView tvName = new TextView(requireContext());
            tvName.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvName.setText(name);
            tvName.setTextSize(13);
            tvName.setTextColor(colorHint);

            TextView tvTime = new TextView(requireContext());
            tvTime.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            tvTime.setText(DateHelper.formatForDisplay(dateStr, "EEE, HH:mm"));
            tvTime.setTextSize(13);
            tvTime.setTextColor(colorSecondary);

            row.addView(tvName);
            row.addView(tvTime);
            llSessionTimes.addView(row);
        }
    }

    private void startCountdown(String isoDateStr) {
        try {
            long millis = DateHelper.toMillis(isoDateStr);
            if (millis == -1) return;
            long diff = millis - System.currentTimeMillis();
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
                    SpannableStringBuilder sb = new SpannableStringBuilder();
                    appendCountdownUnit(sb, String.valueOf(days), "d  ");
                    appendCountdownUnit(sb, String.format(Locale.getDefault(), "%02d", hours), "h  ");
                    appendCountdownUnit(sb, String.format(Locale.getDefault(), "%02d", minutes), "m");
                    tvCountdown.setText(sb, TextView.BufferType.SPANNABLE);
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

    private void appendCountdownUnit(SpannableStringBuilder sb, String number, String unit) {
        sb.append(number);
        int start = sb.length();
        sb.append(unit);
        sb.setSpan(new RelativeSizeSpan(0.5f), start, sb.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private String formatDate(String isoDateStr) {
        return DateHelper.formatFull(isoDateStr);
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
