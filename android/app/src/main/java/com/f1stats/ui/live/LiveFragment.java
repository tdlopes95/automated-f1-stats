package com.f1stats.ui.live;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.f1stats.DateHelper;
import com.f1stats.R;
import com.f1stats.RoundDetailActivity;
import com.f1stats.SeasonHelper;
import com.f1stats.models.QualifyingResult;
import com.f1stats.models.RaceResult;
import com.f1stats.viewmodels.F1ViewModel;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LiveFragment extends Fragment {

    private F1ViewModel viewModel;

    private SwipeRefreshLayout swipeRefresh;
    private CardView cardMain, cardTimeline;
    private ImageView ivCircuitImage, ivCountryFlag;
    private TextView tvRaceName, tvSessionLabel, tvSessionName;
    private TextView tvCountdown, tvLocalTime;
    private LinearLayout llQuickResults, llSessions;
    private TextView btnOpenResults;
    private TextView tvEmpty;

    private List<Map<String, Object>> latestSchedule;
    private List<Map<String, Object>> latestMeetings;
    private Map<String, Object> displayedRace;
    private Map<String, Object> recentSession;

    private final Handler tickHandler = new Handler(Looper.getMainLooper());
    private Runnable tickRunnable;

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

        swipeRefresh   = view.findViewById(R.id.swipe_refresh_weekend);
        cardMain       = view.findViewById(R.id.card_main);
        cardTimeline   = view.findViewById(R.id.card_timeline);
        ivCircuitImage = view.findViewById(R.id.iv_circuit_image);
        ivCountryFlag  = view.findViewById(R.id.iv_country_flag);
        tvRaceName     = view.findViewById(R.id.tv_race_name);
        tvSessionLabel = view.findViewById(R.id.tv_session_label);
        tvSessionName  = view.findViewById(R.id.tv_session_name);
        tvCountdown    = view.findViewById(R.id.tv_countdown);
        tvLocalTime    = view.findViewById(R.id.tv_local_time);
        llQuickResults = view.findViewById(R.id.ll_quick_results);
        llSessions     = view.findViewById(R.id.ll_sessions);
        btnOpenResults = view.findViewById(R.id.btn_open_results);
        tvEmpty        = view.findViewById(R.id.tv_empty_weekend);

        viewModel = new ViewModelProvider(requireActivity()).get(F1ViewModel.class);

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.f1_red));
        swipeRefresh.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_dark));
        swipeRefresh.setOnRefreshListener(this::refresh);

        observeViewModel();

        viewModel.fetchSchedule(SeasonHelper.getCurrentYear());
        viewModel.fetchMeetings(SeasonHelper.getCurrentYear());
    }

    private void refresh() {
        latestSchedule = null;
        latestMeetings = null;
        viewModel.fetchSchedule(SeasonHelper.getCurrentYear());
        viewModel.fetchMeetings(SeasonHelper.getCurrentYear());
    }

    private void observeViewModel() {
        viewModel.getSchedule().observe(getViewLifecycleOwner(), schedule -> {
            latestSchedule = schedule;
            mergeAndDisplay();
        });

        viewModel.getMeetings().observe(getViewLifecycleOwner(), meetings -> {
            latestMeetings = meetings;
            mergeAndDisplay();
        });

        viewModel.getScheduleLoading().observe(getViewLifecycleOwner(), loading ->
                swipeRefresh.setRefreshing(loading != null && loading));

        viewModel.getRaceResults().observe(getViewLifecycleOwner(), results -> {
            if (results != null && !results.isEmpty() && recentSession != null
                    && llQuickResults.getVisibility() == View.VISIBLE) {
                populateRaceResults(results);
            }
        });

        viewModel.getQualifyingResults().observe(getViewLifecycleOwner(), results -> {
            if (results != null && !results.isEmpty() && recentSession != null
                    && llQuickResults.getVisibility() == View.VISIBLE) {
                populateQualifyingResults(results);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void mergeAndDisplay() {
        if (latestSchedule == null) return;

        if (latestMeetings != null) {
            for (Map<String, Object> race : latestSchedule) {
                String raceName = str(race, "race_name", "").toLowerCase();
                for (Map<String, Object> meeting : latestMeetings) {
                    String meetingName = str(meeting, "meeting_name", "").toLowerCase();
                    if (!meetingName.isEmpty() && (meetingName.equals(raceName)
                            || meetingName.contains(raceName) || raceName.contains(meetingName))) {
                        race.put("circuit_image", meeting.get("circuit_image"));
                        race.put("country_flag", meeting.get("country_flag"));
                        break;
                    }
                }
            }
        }

        Map<String, Object> targetRace = findCurrentOrNextRace(latestSchedule);
        if (targetRace == null) {
            swipeRefresh.setRefreshing(false);
            cardMain.setVisibility(View.GONE);
            cardTimeline.setVisibility(View.GONE);
            tvEmpty.setText("No upcoming race weekends");
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        tvEmpty.setVisibility(View.GONE);
        cardMain.setVisibility(View.VISIBLE);
        displayedRace = targetRace;

        tvRaceName.setText(str(targetRace, "race_name", "Race"));

        String circuitImage = (String) targetRace.get("circuit_image");
        if (circuitImage != null && !circuitImage.isEmpty() && isAdded()) {
            Glide.with(this).load(circuitImage).into(ivCircuitImage);
        }
        String countryFlag = (String) targetRace.get("country_flag");
        if (countryFlag != null && !countryFlag.isEmpty() && isAdded()) {
            Glide.with(this).load(countryFlag).into(ivCountryFlag);
        }

        final String finalCircuitImage = circuitImage;
        ivCircuitImage.setOnClickListener(v -> {
            if (finalCircuitImage == null || finalCircuitImage.isEmpty()) return;
            android.content.Intent intent = new android.content.Intent(
                    requireContext(), com.f1stats.TrackDetailActivity.class);
            intent.putExtra(com.f1stats.TrackDetailActivity.EXTRA_CIRCUIT_IMAGE, finalCircuitImage);
            intent.putExtra(com.f1stats.TrackDetailActivity.EXTRA_CIRCUIT_NAME,
                    str(targetRace, "circuit", ""));
            intent.putExtra(com.f1stats.TrackDetailActivity.EXTRA_COUNTRY,
                    str(targetRace, "country", ""));
            intent.putExtra(com.f1stats.TrackDetailActivity.EXTRA_LOCALITY,
                    str(targetRace, "locality", ""));
            intent.putExtra(com.f1stats.TrackDetailActivity.EXTRA_COUNTRY_FLAG,
                    (String) targetRace.get("country_flag"));
            startActivity(intent);
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        List<Map<String, Object>> sessions = (List<Map<String, Object>>) targetRace.get("sessions");
        if (sessions == null || sessions.isEmpty()) return;

        long now = System.currentTimeMillis();
        Map<String, Object> nextSess = null;
        Map<String, Object> lastSess = null;

        for (Map<String, Object> session : sessions) {
            long t = DateHelper.toMillis(str(session, "datetime", null));
            if (t > now && nextSess == null) nextSess = session;
            if (t > 0 && t <= now) lastSess = session;
        }

        buildTimeline(sessions, nextSess);
        cardTimeline.setVisibility(View.VISIBLE);

        // Decide main card content
        boolean showingResults = false;
        if (lastSess != null) {
            long endedAgo = now - DateHelper.toMillis(str(lastSess, "datetime", null));
            if (endedAgo < 6 * 3600_000L && isResultSession(str(lastSess, "name", ""))) {
                showingResults = true;
                recentSession = lastSess;
                showLatestResultsCard(lastSess, targetRace);
            }
        }

        if (!showingResults) {
            recentSession = null;
            if (nextSess != null) {
                showNextSessionCard(nextSess);
            } else {
                showWeekendCompleteState();
            }
        }
    }

    private void showNextSessionCard(Map<String, Object> session) {
        stopCountdown();

        tvSessionLabel.setText("NEXT");
        tvSessionLabel.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.f1_red));
        tvSessionName.setText(str(session, "name", "Session"));
        tvCountdown.setVisibility(View.VISIBLE);
        tvLocalTime.setVisibility(View.VISIBLE);
        llQuickResults.setVisibility(View.GONE);
        btnOpenResults.setVisibility(View.GONE);

        String dt = str(session, "datetime", null);
        tvLocalTime.setText(DateHelper.formatShort(dt));
        startCountdown(DateHelper.toMillis(dt));
    }

    private void showLatestResultsCard(Map<String, Object> session, Map<String, Object> race) {
        stopCountdown();

        tvSessionLabel.setText("JUST ENDED");
        tvSessionLabel.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.status_green));
        tvSessionName.setText(str(race, "race_name", "") + " — " + str(session, "name", "Session"));
        tvCountdown.setVisibility(View.GONE);
        tvLocalTime.setText("Ended " + DateHelper.formatShort(str(session, "datetime", null)));
        tvLocalTime.setVisibility(View.VISIBLE);
        llQuickResults.setVisibility(View.VISIBLE);
        llQuickResults.removeAllViews();
        btnOpenResults.setVisibility(View.VISIBLE);

        Object roundObj = race.get("round");
        int round = roundObj != null ? ((Number) roundObj).intValue() : 0;
        int year = SeasonHelper.getCurrentYear();
        String sessName = str(session, "name", "");

        if ("Race".equals(sessName) || "Sprint".equals(sessName)) {
            viewModel.fetchResults(year, round, sessName);
        } else if ("Qualifying".equals(sessName)) {
            viewModel.fetchQualifyingResults(year, round);
        }

        btnOpenResults.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), RoundDetailActivity.class);
            intent.putExtra(RoundDetailActivity.EXTRA_ROUND, round);
            intent.putExtra(RoundDetailActivity.EXTRA_YEAR, year);
            intent.putExtra(RoundDetailActivity.EXTRA_RACE_NAME, str(race, "race_name", ""));
            intent.putExtra(RoundDetailActivity.EXTRA_CIRCUIT, str(race, "circuit", ""));
            intent.putExtra(RoundDetailActivity.EXTRA_CIRCUIT_IMAGE, (String) race.get("circuit_image"));
            intent.putExtra(RoundDetailActivity.EXTRA_COUNTRY_FLAG, (String) race.get("country_flag"));
            startActivity(intent);
            requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    private void showWeekendCompleteState() {
        stopCountdown();
        tvSessionLabel.setText("COMPLETED");
        tvSessionLabel.setBackgroundColor(
                ContextCompat.getColor(requireContext(), R.color.no_session_gray));
        tvSessionName.setText("Race weekend finished");
        tvCountdown.setVisibility(View.GONE);
        tvLocalTime.setVisibility(View.GONE);
        llQuickResults.setVisibility(View.GONE);
        btnOpenResults.setVisibility(View.GONE);

        if (displayedRace != null) {
            btnOpenResults.setVisibility(View.VISIBLE);
            Object roundObj = displayedRace.get("round");
            int round = roundObj != null ? ((Number) roundObj).intValue() : 0;
            int year = SeasonHelper.getCurrentYear();
            btnOpenResults.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), RoundDetailActivity.class);
                intent.putExtra(RoundDetailActivity.EXTRA_ROUND, round);
                intent.putExtra(RoundDetailActivity.EXTRA_YEAR, year);
                intent.putExtra(RoundDetailActivity.EXTRA_RACE_NAME, str(displayedRace, "race_name", ""));
                intent.putExtra(RoundDetailActivity.EXTRA_CIRCUIT, str(displayedRace, "circuit", ""));
                intent.putExtra(RoundDetailActivity.EXTRA_CIRCUIT_IMAGE, (String) displayedRace.get("circuit_image"));
                intent.putExtra(RoundDetailActivity.EXTRA_COUNTRY_FLAG, (String) displayedRace.get("country_flag"));
                startActivity(intent);
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });
        }
    }

    private void populateRaceResults(List<RaceResult> results) {
        if (!isAdded()) return;
        llQuickResults.removeAllViews();
        int count = 0;
        for (RaceResult r : results) {
            if (count++ >= 10) break;
            String code = r.getDriver() != null ? r.getDriver().getCode() : "---";
            String team = r.getConstructor() != null ? r.getConstructor().getName() : "";
            addResultRow(r.getPosition(), code, team, r.getDisplayTime());
        }
    }

    private void populateQualifyingResults(List<QualifyingResult> results) {
        if (!isAdded()) return;
        llQuickResults.removeAllViews();
        int count = 0;
        for (QualifyingResult q : results) {
            if (count++ >= 10) break;
            String code = q.getDriver() != null ? q.getDriver().getCode() : "---";
            String team = q.getConstructor() != null ? q.getConstructor().getName() : "";
            addResultRow(q.getPosition(), code, team, q.getBestTime());
        }
    }

    private void addResultRow(String position, String code, String team, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int vpad = dpToPx(5);
        row.setPadding(0, vpad, 0, vpad);

        TextView tvPos = new TextView(requireContext());
        tvPos.setText(position);
        tvPos.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        tvPos.setTextSize(13f);
        tvPos.setMinWidth(dpToPx(28));

        TextView tvDriver = new TextView(requireContext());
        tvDriver.setText(code + "  " + team);
        tvDriver.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        tvDriver.setTextSize(13f);
        LinearLayout.LayoutParams driverParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvDriver.setLayoutParams(driverParams);

        TextView tvVal = new TextView(requireContext());
        tvVal.setText(value);
        tvVal.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        tvVal.setTextSize(12f);
        tvVal.setGravity(android.view.Gravity.END);

        row.addView(tvPos);
        row.addView(tvDriver);
        row.addView(tvVal);

        View divider = new View(requireContext());
        divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.bg_divider));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        llQuickResults.addView(row);
        llQuickResults.addView(divider);
    }

    @SuppressWarnings("unchecked")
    private void buildTimeline(List<Map<String, Object>> sessions, Map<String, Object> nextSess) {
        llSessions.removeAllViews();
        long now = System.currentTimeMillis();
        String nextDt = nextSess != null ? str(nextSess, "datetime", null) : null;

        for (Map<String, Object> session : sessions) {
            String name = str(session, "name", "");
            String dt = str(session, "datetime", null);
            long millis = DateHelper.toMillis(dt);
            boolean isNext = dt != null && dt.equals(nextDt);
            boolean isDone = millis > 0 && millis < now;

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int pad = dpToPx(7);
            row.setPadding(0, pad, 0, pad);

            TextView tvIcon = new TextView(requireContext());
            tvIcon.setText(isDone ? "✓" : isNext ? "►" : "○");
            tvIcon.setTextColor(isDone
                    ? ContextCompat.getColor(requireContext(), R.color.status_green)
                    : isNext
                    ? ContextCompat.getColor(requireContext(), R.color.f1_red)
                    : ContextCompat.getColor(requireContext(), R.color.text_hint));
            tvIcon.setTextSize(14f);
            tvIcon.setMinWidth(dpToPx(24));

            TextView tvName = new TextView(requireContext());
            tvName.setText(name);
            tvName.setTextColor(isNext || isDone
                    ? ContextCompat.getColor(requireContext(), R.color.text_primary)
                    : ContextCompat.getColor(requireContext(), R.color.text_secondary));
            tvName.setTextSize(14f);
            if (isNext) tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvName.setLayoutParams(nameParams);

            TextView tvTime = new TextView(requireContext());
            tvTime.setText(DateHelper.formatShort(dt));
            tvTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            tvTime.setTextSize(12f);
            tvTime.setGravity(android.view.Gravity.END);

            row.addView(tvIcon);
            row.addView(tvName);
            row.addView(tvTime);
            llSessions.addView(row);
        }
    }

    private void startCountdown(long targetMs) {
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded() || tvCountdown == null) return;
                long remaining = targetMs - System.currentTimeMillis();
                if (remaining <= 0) {
                    tvCountdown.setText("Starting now");
                    mergeAndDisplay();
                    return;
                }
                long days = TimeUnit.MILLISECONDS.toDays(remaining);
                long hours = TimeUnit.MILLISECONDS.toHours(remaining) % 24;
                long mins = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60;
                long secs = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60;
                if (days > 0) {
                    tvCountdown.setText(String.format(Locale.getDefault(),
                            "%dd  %02d:%02d:%02d", days, hours, mins, secs));
                } else {
                    tvCountdown.setText(String.format(Locale.getDefault(),
                            "%02d:%02d:%02d", hours, mins, secs));
                }
                tickHandler.postDelayed(this, 1000);
            }
        };
        tickHandler.post(tickRunnable);
    }

    private void stopCountdown() {
        if (tickRunnable != null) tickHandler.removeCallbacks(tickRunnable);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findCurrentOrNextRace(List<Map<String, Object>> schedule) {
        long now = System.currentTimeMillis();
        for (Map<String, Object> race : schedule) {
            List<Map<String, Object>> sessions = (List<Map<String, Object>>) race.get("sessions");
            if (sessions == null) continue;
            for (Map<String, Object> s : sessions) {
                if ("Race".equals(s.get("name"))) {
                    long raceMs = DateHelper.toMillis(str(s, "datetime", null));
                    // Show this weekend until 6h after the race
                    if (raceMs + 6 * 3600_000L >= now) return race;
                    break;
                }
            }
        }
        return null;
    }

    private boolean isResultSession(String name) {
        return "Race".equals(name) || "Qualifying".equals(name)
                || "Sprint".equals(name) || "Sprint Qualifying".equals(name)
                || "Sprint Shootout".equals(name);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopCountdown();
    }

    private String str(Map<String, Object> map, String key, String fallback) {
        Object val = map != null ? map.get(key) : null;
        return val != null ? val.toString() : fallback;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
