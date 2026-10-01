package com.f1stats.ui.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.CountDownTimer;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.f1stats.DateHelper;
import com.f1stats.DriverHelper;
import com.f1stats.R;
import com.f1stats.home.HomeCardType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NEXT_RACE: circuit image, countdown to the next session, weekend timeline and session list.
 * Also renders the Weekend Complete and Offseason states.
 */
class NextRaceCardHolder extends HomeCardHolder<HomeCardState.NextRace> {

    /** Sessions have no end time in the data; assume each runs at most this long. */
    private static final long SESSION_DURATION_MS = 3 * 60 * 60 * 1000L;

    private final Context context;
    private final TextView tvNextRaceName, tvNextRaceCircuit, tvNextRaceDate, tvNextRaceFlag;
    private final TextView tvCountdown;
    private final TextView tvNextSession;
    private final TextView tvHeroStateTitle, tvHeroStateSubtitle;
    private final LinearLayout layoutHeroState;
    private final ImageView ivNextRaceCircuit;
    private final LinearLayout llSessionTimes;
    private final LinearLayout llWeekendTimeline;

    private CountDownTimer countDownTimer;

    NextRaceCardHolder(@NonNull View itemView) {
        super(itemView, HomeCardType.NEXT_RACE);
        context = itemView.getContext();
        tvNextRaceName      = itemView.findViewById(R.id.tv_next_race_name);
        tvNextRaceCircuit   = itemView.findViewById(R.id.tv_next_race_circuit);
        tvNextRaceDate      = itemView.findViewById(R.id.tv_next_race_date);
        tvNextRaceFlag      = itemView.findViewById(R.id.tv_next_race_flag);
        tvCountdown         = itemView.findViewById(R.id.tv_countdown);
        tvNextSession       = itemView.findViewById(R.id.tv_next_session);
        tvHeroStateTitle    = itemView.findViewById(R.id.tv_hero_state_title);
        tvHeroStateSubtitle = itemView.findViewById(R.id.tv_hero_state_subtitle);
        layoutHeroState     = itemView.findViewById(R.id.layout_hero_state);
        ivNextRaceCircuit   = itemView.findViewById(R.id.iv_next_race_circuit);
        llSessionTimes      = itemView.findViewById(R.id.ll_session_times);
        llWeekendTimeline   = itemView.findViewById(R.id.ll_weekend_timeline);
    }

    @Override
    void bind(@NonNull HomeCardState.NextRace state, @Nullable Map<String, String> headshots) {
        cancelCountdown();

        if (state.circuitImageUrl != null && !state.circuitImageUrl.isEmpty()) {
            Glide.with(ivNextRaceCircuit).load(state.circuitImageUrl).into(ivNextRaceCircuit);
        } else {
            Glide.with(ivNextRaceCircuit).clear(ivNextRaceCircuit);   // keep the placeholder
        }

        Map<String, Object> race = state.race;
        if (race == null) {
            if (state.offseason) showOffseasonState();
            return;
        }

        tvNextRaceName.setText(getStr(race, "race_name",
                context.getString(R.string.home_unknown_race)));
        tvNextRaceCircuit.setText(getStr(race, "circuit", ""));
        tvNextRaceFlag.setText(DriverHelper.getFlagForCountry(getStr(race, "country", "")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sessions = (List<Map<String, Object>>) race.get("sessions");
        if (sessions != null) {
            buildSessionTimes(sessions);
        } else {
            // No session data: show the race name without sessions
            tvNextRaceDate.setText("");
            tvCountdown.setText("");
            tvNextSession.setVisibility(View.GONE);
            llWeekendTimeline.setVisibility(View.GONE);
            llSessionTimes.removeAllViews();
            showNormalCountdownState();
        }
    }

    @Override
    void onNoData() {
        cancelCountdown();
    }

    @Override
    void release() {
        super.release();
        cancelCountdown();
    }

    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    // ── Weekend timeline ──────────────────────────────────────────────────────

    private void buildSessionTimes(List<Map<String, Object>> sessions) {
        buildWeekendTimeline(sessions);
        updateNextSessionLabel(sessions);
        buildSessionList(sessions);

        // Countdown targets the next relevant session, not always the race
        Map<String, Object> nextSession = findNextUpcomingSession(sessions);
        tvNextRaceDate.setText("");
        if (nextSession != null) {
            String dateStr = (String) nextSession.get("datetime");
            if (dateStr != null) {
                // Show race date as context when next session is the race itself
                if ("Race".equals(nextSession.get("name"))) {
                    tvNextRaceDate.setText(DateHelper.formatFull(dateStr));
                }
                startCountdown(dateStr);
            }
            showNormalCountdownState();
        } else {
            // All sessions complete
            showWeekendCompleteState();
        }

        // Always show full race date for context if we have it
        if (tvNextRaceDate.getText().toString().isEmpty()) {
            for (Map<String, Object> session : sessions) {
                if ("Race".equals(session.get("name"))) {
                    String raceDate = (String) session.get("datetime");
                    if (raceDate != null) {
                        tvNextRaceDate.setText(DateHelper.formatFull(raceDate));
                    }
                    break;
                }
            }
        }
    }

    /** Returns the first session that has not yet ended (current or upcoming). */
    private Map<String, Object> findNextUpcomingSession(List<Map<String, Object>> sessions) {
        long now = System.currentTimeMillis();
        for (Map<String, Object> session : sessions) {
            String dateStr = (String) session.get("datetime");
            if (dateStr == null) continue;
            long millis = DateHelper.toMillis(dateStr);
            if (millis == -1) continue;
            if (millis + SESSION_DURATION_MS > now) {
                return session;
            }
        }
        return null;
    }

    /** Show normal countdown + session label, hide state panel. */
    private void showNormalCountdownState() {
        tvCountdown.setVisibility(View.VISIBLE);
        layoutHeroState.setVisibility(View.GONE);
    }

    /** All sessions done, awaiting the next race. */
    private void showWeekendCompleteState() {
        cancelCountdown();
        tvCountdown.setVisibility(View.GONE);
        tvNextSession.setText(R.string.home_weekend_complete);
        tvNextSession.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        tvNextSession.setVisibility(View.VISIBLE);
        tvHeroStateTitle.setText(R.string.home_weekend_complete);
        tvHeroStateSubtitle.setText(R.string.home_weekend_complete_subtitle);
        layoutHeroState.setVisibility(View.VISIBLE);
        llWeekendTimeline.setVisibility(View.VISIBLE);
    }

    /** No upcoming race in the near future. */
    private void showOffseasonState() {
        cancelCountdown();
        tvCountdown.setVisibility(View.GONE);
        tvNextSession.setText(R.string.home_offseason);
        tvNextSession.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary));
        tvNextSession.setVisibility(View.VISIBLE);
        tvHeroStateTitle.setText(R.string.home_winter_break);
        tvHeroStateSubtitle.setText(R.string.home_winter_break_subtitle);
        layoutHeroState.setVisibility(View.VISIBLE);
    }

    private void buildWeekendTimeline(List<Map<String, Object>> sessions) {
        llWeekendTimeline.removeAllViews();

        float d = context.getResources().getDisplayMetrics().density;
        int dotSizePx   = Math.round(10 * d);
        int dotTopPx    = Math.round(8 * d);
        int lineTopPx   = dotTopPx + (dotSizePx / 2);
        int labelTopPx  = Math.round(4 * d);
        int totalHeight = Math.round(44 * d);

        int colorDone    = ContextCompat.getColor(context, R.color.status_green);
        int colorActive  = Color.WHITE;
        int colorPending = ContextCompat.getColor(context, R.color.bg_elevated);
        int colorLine    = ContextCompat.getColor(context, R.color.bg_elevated);
        int labelDone    = ContextCompat.getColor(context, R.color.text_tertiary);
        int labelActive  = Color.WHITE;
        int labelPending = ContextCompat.getColor(context, R.color.text_hint);

        FrameLayout frame = new FrameLayout(context);
        frame.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, totalHeight));

        View line = new View(context);
        FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(1 * d)));
        lineParams.topMargin = lineTopPx;
        line.setBackgroundColor(colorLine);
        frame.addView(line, lineParams);

        LinearLayout nodesRow = new LinearLayout(context);
        nodesRow.setOrientation(LinearLayout.HORIZONTAL);
        frame.addView(nodesRow, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        for (Map<String, Object> session : sessions) {
            String name    = (String) session.get("name");
            String dateStr = (String) session.get("datetime");
            if (name == null) continue;

            String abbr   = getSessionAbbr(name);
            int    status = dateStr != null ? getSessionStatus(dateStr) : 1;

            int dotColor   = status < 0 ? colorDone : (status == 0 ? colorActive : colorPending);
            int labelColor = status < 0 ? labelDone : (status == 0 ? labelActive : labelPending);
            boolean filled = status <= 0;

            LinearLayout node = new LinearLayout(context);
            node.setOrientation(LinearLayout.VERTICAL);
            node.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams nodeParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT);
            nodeParams.weight = 1;
            node.setLayoutParams(nodeParams);

            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            if (filled) {
                dotBg.setColor(dotColor);
            } else {
                dotBg.setColor(ContextCompat.getColor(context, R.color.bg_dark));
                dotBg.setStroke(Math.round(1.5f * d), colorPending);
            }
            View dot = new View(context);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSizePx, dotSizePx);
            dotParams.topMargin = dotTopPx;
            dot.setBackground(dotBg);
            node.addView(dot, dotParams);

            TextView label = new TextView(context);
            label.setText(abbr);
            label.setTextSize(10f);
            label.setTextColor(labelColor);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = labelTopPx;
            node.addView(label, labelParams);

            nodesRow.addView(node);
        }

        llWeekendTimeline.addView(frame);
        llWeekendTimeline.setVisibility(View.VISIBLE);
    }

    /** The eyebrow above the countdown: which session is next, or live status. */
    private void updateNextSessionLabel(List<Map<String, Object>> sessions) {
        long now = System.currentTimeMillis();
        for (Map<String, Object> session : sessions) {
            String dateStr = (String) session.get("datetime");
            if (dateStr == null) continue;
            long millis = DateHelper.toMillis(dateStr);
            if (millis == -1) continue;
            if (millis + SESSION_DURATION_MS > now) {
                String name = (String) session.get("name");
                String abbr = getSessionAbbr(name != null ? name : "");
                if (millis <= now) {
                    tvNextSession.setText(context.getString(R.string.home_session_live, abbr));
                    tvNextSession.setTextColor(
                            ContextCompat.getColor(context, R.color.color_race_live));
                } else {
                    String time = DateHelper.formatForDisplay(dateStr, "EEE, HH:mm");
                    tvNextSession.setText(context.getString(R.string.home_session_next, abbr, time));
                    tvNextSession.setTextColor(
                            ContextCompat.getColor(context, R.color.text_secondary));
                }
                tvNextSession.setVisibility(View.VISIBLE);
                return;
            }
        }
        // All sessions finished: Weekend Complete is handled in buildSessionTimes
        tvNextSession.setVisibility(View.GONE);
    }

    private void buildSessionList(List<Map<String, Object>> sessions) {
        llSessionTimes.removeAllViews();
        float density = context.getResources().getDisplayMetrics().density;
        int topMarginPx = Math.round(4 * density);
        long now = System.currentTimeMillis();

        for (Map<String, Object> session : sessions) {
            String name    = (String) session.get("name");
            String dateStr = (String) session.get("datetime");
            if (name == null || dateStr == null) continue;

            long millis = DateHelper.toMillis(dateStr);
            boolean isDone = millis != -1 && (millis + SESSION_DURATION_MS) < now;
            boolean isLive = millis != -1 && millis <= now && (millis + SESSION_DURATION_MS) > now;

            int nameColor = isDone
                    ? ContextCompat.getColor(context, R.color.text_hint)
                    : (isLive
                        ? ContextCompat.getColor(context, R.color.color_race_live)
                        : ContextCompat.getColor(context, R.color.text_secondary));
            int timeColor = isDone
                    ? ContextCompat.getColor(context, R.color.text_hint)
                    : ContextCompat.getColor(context, R.color.text_secondary);

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = topMarginPx;
            row.setLayoutParams(rowParams);

            TextView tvName = new TextView(context);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvName.setText(name);
            tvName.setTextSize(13f);
            tvName.setTextColor(nameColor);

            TextView tvTime = new TextView(context);
            tvTime.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            tvTime.setText(isLive
                    ? context.getString(R.string.home_session_live_short)
                    : DateHelper.formatForDisplay(dateStr, "EEE, HH:mm"));
            tvTime.setTextSize(13f);
            tvTime.setTextColor(isLive
                    ? ContextCompat.getColor(context, R.color.color_race_live)
                    : timeColor);

            row.addView(tvName);
            row.addView(tvTime);
            llSessionTimes.addView(row);
        }
    }

    // ── Countdown ─────────────────────────────────────────────────────────────

    /** Counts down to the given session (any session, not just the race). */
    private void startCountdown(String isoDateStr) {
        long millis = DateHelper.toMillis(isoDateStr);
        if (millis == -1) return;

        long diff = millis - System.currentTimeMillis();
        if (diff <= 0) {
            // Session is currently live
            if (millis + SESSION_DURATION_MS > System.currentTimeMillis()) {
                showLiveNow();
            }
            return;
        }

        tvCountdown.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        countDownTimer = new CountDownTimer(diff, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long days    = millisUntilFinished / (1000 * 60 * 60 * 24);
                long hours   = (millisUntilFinished % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
                long minutes = (millisUntilFinished % (1000 * 60 * 60)) / (1000 * 60);
                SpannableStringBuilder sb = new SpannableStringBuilder();
                if (days > 0) {
                    appendCountdownUnit(sb, String.valueOf(days),
                            context.getString(R.string.home_countdown_days));
                    appendCountdownUnit(sb, String.format(Locale.getDefault(), "%02d", hours),
                            context.getString(R.string.home_countdown_hours_short));
                } else {
                    appendCountdownUnit(sb, String.format(Locale.getDefault(), "%02d", hours),
                            context.getString(R.string.home_countdown_hours));
                    appendCountdownUnit(sb, String.format(Locale.getDefault(), "%02d", minutes),
                            context.getString(R.string.home_countdown_minutes));
                }
                tvCountdown.setText(sb, TextView.BufferType.SPANNABLE);
            }
            @Override
            public void onFinish() {
                showLiveNow();
            }
        }.start();
    }

    private void showLiveNow() {
        tvCountdown.setText(R.string.home_countdown_live);
        tvCountdown.setTextColor(ContextCompat.getColor(context, R.color.color_race_live));
    }

    private static void appendCountdownUnit(SpannableStringBuilder sb, String number, String unit) {
        sb.append(number);
        int start = sb.length();
        sb.append(unit);
        sb.setSpan(new RelativeSizeSpan(0.45f), start, sb.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns -1 = done, 0 = live/active, 1 = upcoming. */
    private static int getSessionStatus(String dateStr) {
        long millis = DateHelper.toMillis(dateStr);
        if (millis == -1) return 1;
        long now = System.currentTimeMillis();
        if (millis + SESSION_DURATION_MS < now) return -1;
        if (millis < now) return 0;
        return 1;
    }

    private String getSessionAbbr(String name) {
        switch (name) {
            case "Practice 1":       return context.getString(R.string.session_abbr_fp1);
            case "Practice 2":       return context.getString(R.string.session_abbr_fp2);
            case "Practice 3":       return context.getString(R.string.session_abbr_fp3);
            case "Sprint Shootout":  return context.getString(R.string.session_abbr_sprint_shootout);
            case "Sprint":           return context.getString(R.string.session_abbr_sprint);
            case "Qualifying":       return context.getString(R.string.session_abbr_qualifying);
            case "Race":             return context.getString(R.string.session_abbr_race);
            default:
                String upper = name.toUpperCase(Locale.getDefault());
                return upper.substring(0, Math.min(4, upper.length()));
        }
    }

    private static String getStr(Map<String, Object> map, String key, String fallback) {
        Object val = map.get(key);
        return val != null ? val.toString() : fallback;
    }
}
