package com.f1stats.ui.analysis;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.f1stats.R;
import com.f1stats.home.HomeLayoutStore;
import com.f1stats.models.RaceAnalysis;
import com.f1stats.ui.charts.ChartStyle;
import com.f1stats.ui.charts.LapTimeMarker;
import com.f1stats.util.LapTimes;
import com.f1stats.util.TeamColors;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Binds the round detail "Analysis" tab (view_race_analysis.xml): driver chips, the lap
 * position chart, the lap time chart and the pit stop list.
 */
public class RaceAnalysisSection {

    /** Most drivers the lap time chart compares at once. */
    public static final int MAX_SELECTED = 6;
    private static final int PODIUM = 3;
    private static final int PIT_DATA_FIRST_SEASON = 2011;

    private static final float LINE_SELECTED_DP = 2.5f;
    private static final float LINE_MUTED_DP = 1f;
    private static final float LINE_LAP_TIME_DP = 1.5f;

    private final Context context;
    private final ShimmerFrameLayout shimmer;
    private final View errorLayout;
    private final View content;
    private final ChipGroup chipGroup;
    private final View charts;
    private final TextView tvNoLaps;
    private final LineChart positionChart;
    private final LineChart lapTimeChart;
    private final TextView tvLapTimesEmpty;
    private final MaterialSwitch hideSlowLaps;
    private final LinearLayout pitStops;
    private final TextView tvPitNote;

    @Nullable private RaceAnalysis analysis;
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, Integer> colours = new HashMap<>();

    public RaceAnalysisSection(@NonNull View root, @NonNull Runnable onRetry) {
        context         = root.getContext();
        shimmer         = root.findViewById(R.id.shimmer_analysis);
        errorLayout     = root.findViewById(R.id.layout_analysis_error);
        content         = root.findViewById(R.id.layout_analysis_content);
        chipGroup       = root.findViewById(R.id.chip_group_drivers);
        charts          = root.findViewById(R.id.layout_analysis_charts);
        tvNoLaps        = root.findViewById(R.id.tv_analysis_no_laps);
        positionChart   = root.findViewById(R.id.chart_positions);
        lapTimeChart    = root.findViewById(R.id.chart_lap_times);
        tvLapTimesEmpty = root.findViewById(R.id.tv_lap_times_empty);
        hideSlowLaps    = root.findViewById(R.id.switch_hide_slow_laps);
        pitStops        = root.findViewById(R.id.ll_analysis_pit_stops);
        tvPitNote       = root.findViewById(R.id.tv_analysis_pit_note);

        root.findViewById(R.id.btn_analysis_retry).setOnClickListener(v -> onRetry.run());
        hideSlowLaps.setOnCheckedChangeListener((button, checked) -> renderLapTimeChart());
        setupPositionChart();
        setupLapTimeChart();
    }

    // ── States ────────────────────────────────────────────────────────────────

    public void showLoading() {
        if (analysis != null) return; // keep showing what we have
        errorLayout.setVisibility(View.GONE);
        content.setVisibility(View.GONE);
        shimmer.setVisibility(View.VISIBLE);
        shimmer.startShimmer();
    }

    public void showError() {
        if (analysis != null) return;
        stopShimmer();
        content.setVisibility(View.GONE);
        errorLayout.setVisibility(View.VISIBLE);
    }

    public void bind(@NonNull RaceAnalysis data) {
        boolean sameRace = analysis != null
                && analysis.year == data.year && analysis.round == data.round;
        analysis = data;
        stopShimmer();
        errorLayout.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);

        colours.clear();
        for (RaceAnalysis.Driver d : data.drivers) {
            colours.put(d.driverId, TeamColors.get(context, d.constructorId, d.constructorName, null));
        }
        if (!sameRace) selectDefaults(data);

        buildChips(data);
        boolean laps = data.lapsAvailable && data.totalLaps > 0;
        charts.setVisibility(laps ? View.VISIBLE : View.GONE);
        tvNoLaps.setVisibility(laps ? View.GONE : View.VISIBLE);
        if (laps) {
            renderPositionChart();
            renderLapTimeChart();
        }
        renderPitStops(data);
    }

    private void stopShimmer() {
        shimmer.stopShimmer();
        shimmer.setVisibility(View.GONE);
    }

    // ── Driver selection ──────────────────────────────────────────────────────

    /** The podium plus the favourite driver, if they took part. */
    private void selectDefaults(RaceAnalysis data) {
        selected.clear();
        for (int i = 0; i < Math.min(PODIUM, data.drivers.size()); i++) {
            selected.add(data.drivers.get(i).driverId);
        }
        String favourite = HomeLayoutStore.getInstance(context).getFavouriteDriverId();
        if (favourite != null && colours.containsKey(favourite)) selected.add(favourite);
    }

    private void buildChips(RaceAnalysis data) {
        chipGroup.removeAllViews();
        int surface = ContextCompat.getColor(context, R.color.bg_surface);
        for (RaceAnalysis.Driver d : data.drivers) {
            int colour = colourOf(d.driverId);
            Chip chip = new Chip(context);
            chip.setText(d.label());
            chip.setCheckable(true);
            chip.setCheckedIconVisible(false);
            chip.setChecked(selected.contains(d.driverId));
            chip.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            chip.setChipStrokeColor(ColorStateList.valueOf(colour));
            chip.setChipStrokeWidth(context.getResources().getDisplayMetrics().density);
            chip.setChipBackgroundColor(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{ColorUtils.setAlphaComponent(colour, 0x66), surface}));
            chip.setOnClickListener(v -> toggle(chip, d.driverId));
            chipGroup.addView(chip);
        }
    }

    private void toggle(Chip chip, String driverId) {
        if (selected.contains(driverId)) {
            selected.remove(driverId);
        } else if (selected.size() >= MAX_SELECTED) {
            chip.setChecked(false);
            Toast.makeText(context, context.getString(R.string.analysis_max_selected, MAX_SELECTED),
                    Toast.LENGTH_SHORT).show();
            return;
        } else {
            selected.add(driverId);
        }
        chip.setChecked(selected.contains(driverId));
        if (analysis != null && analysis.lapsAvailable) {
            renderPositionChart();
            renderLapTimeChart();
        }
    }

    @ColorInt
    private int colourOf(String driverId) {
        Integer colour = colours.get(driverId);
        return colour != null ? colour : ContextCompat.getColor(context, R.color.team_default);
    }

    // ── Position chart ────────────────────────────────────────────────────────

    private void setupPositionChart() {
        ChartStyle.apply(positionChart);
        positionChart.setHighlightPerTapEnabled(false);
        positionChart.setHighlightPerDragEnabled(false);
        YAxis y = positionChart.getAxisLeft();
        y.setInverted(true);
        y.setGranularity(1f);
        y.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return context.getString(R.string.analysis_position, Math.round(value));
            }
        });
    }

    private void renderPositionChart() {
        RaceAnalysis data = analysis;
        if (data == null) return;
        int muted = ContextCompat.getColor(context, R.color.chart_line_muted);
        Map<String, Set<Integer>> pitLaps = pitLapsByDriver(data);

        List<LineDataSet> background = new ArrayList<>();
        List<LineDataSet> foreground = new ArrayList<>();
        int maxPosition = 1;
        for (Map.Entry<String, List<Integer>> row : orderedRows(data, data.positions).entrySet()) {
            String driverId = row.getKey();
            boolean isSelected = selected.contains(driverId);
            Set<Integer> pits = isSelected ? pitLaps.get(driverId) : null;
            List<Entry> entries = new ArrayList<>();
            List<Integer> positions = row.getValue();
            for (int i = 0; i < positions.size(); i++) {
                Integer position = positions.get(i);
                if (position == null) continue;
                maxPosition = Math.max(maxPosition, position);
                int lap = i + 1;
                Entry entry = new Entry(lap, position);
                if (pits != null && pits.contains(lap)) entry.setIcon(pitDot(colourOf(driverId)));
                entries.add(entry);
            }
            if (entries.isEmpty()) continue;
            LineDataSet set = new LineDataSet(entries, driverId);
            styleLine(set, isSelected ? colourOf(driverId) : muted,
                    isSelected ? LINE_SELECTED_DP : LINE_MUTED_DP);
            set.setDrawIcons(isSelected);
            (isSelected ? foreground : background).add(set);
        }
        // Selected drivers are drawn last, on top of the field
        List<ILineDataSet> sets = new ArrayList<>(background);
        sets.addAll(foreground);

        YAxis y = positionChart.getAxisLeft();
        y.setAxisMinimum(1f);
        y.setAxisMaximum(maxPosition);
        y.setLabelCount(Math.min(maxPosition, 6), false);
        positionChart.getXAxis().setAxisMinimum(1f);
        positionChart.getXAxis().setAxisMaximum(Math.max(1, data.totalLaps));
        positionChart.setData(sets.isEmpty() ? null : new LineData(sets));
        positionChart.invalidate();
    }

    /** A small dot marking a pit stop lap, outlined in the driver's team colour. */
    private GradientDrawable pitDot(@ColorInt int colour) {
        int size = context.getResources().getDimensionPixelSize(R.dimen.chart_pit_dot);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ContextCompat.getColor(context, R.color.chart_pit_dot));
        dot.setStroke(Math.max(1, size / 4), colour);
        dot.setSize(size, size);
        return dot;
    }

    private static Map<String, Set<Integer>> pitLapsByDriver(RaceAnalysis data) {
        Map<String, Set<Integer>> laps = new HashMap<>();
        for (RaceAnalysis.Stop stop : data.pitStops) {
            if (stop.driverId == null || stop.lap == null) continue;
            Set<Integer> set = laps.get(stop.driverId);
            if (set == null) laps.put(stop.driverId, set = new HashSet<>());
            set.add(stop.lap);
        }
        return laps;
    }

    // ── Lap time chart ────────────────────────────────────────────────────────

    private void setupLapTimeChart() {
        ChartStyle.apply(lapTimeChart);
        lapTimeChart.setHighlightPerDragEnabled(false);
        LapTimeMarker marker = new LapTimeMarker(context);
        marker.setChartView(lapTimeChart);
        lapTimeChart.setMarker(marker);
        lapTimeChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return LapTimes.formatLapTime((long) value);
            }
        });
    }

    private void renderLapTimeChart() {
        RaceAnalysis data = analysis;
        if (data == null) return;
        boolean hideSlow = hideSlowLaps.isChecked();
        List<ILineDataSet> sets = new ArrayList<>();
        Map<String, String> labels = new HashMap<>();
        for (RaceAnalysis.Driver d : data.drivers) labels.put(d.driverId, d.label());

        for (Map.Entry<String, List<Long>> row : orderedRows(data, data.lapTimesMs).entrySet()) {
            String driverId = row.getKey();
            if (!selected.contains(driverId)) continue;
            List<Long> laps = hideSlow ? LapTimes.withoutSlowLaps(row.getValue()) : row.getValue();
            String label = labels.containsKey(driverId) ? labels.get(driverId) : driverId;
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < laps.size(); i++) {
                Long ms = laps.get(i);
                if (ms != null) entries.add(new Entry(i + 1, ms, label));
            }
            if (entries.isEmpty()) continue;
            LineDataSet set = new LineDataSet(entries, label);
            styleLine(set, colourOf(driverId), LINE_LAP_TIME_DP);
            set.setHighlightEnabled(true);
            set.setHighLightColor(ContextCompat.getColor(context, R.color.chart_highlight));
            set.setDrawHorizontalHighlightIndicator(false);
            sets.add(set);
        }

        boolean empty = sets.isEmpty();
        lapTimeChart.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvLapTimesEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        lapTimeChart.highlightValues(null);
        lapTimeChart.getXAxis().setAxisMinimum(1f);
        lapTimeChart.getXAxis().setAxisMaximum(Math.max(1, data.totalLaps));
        lapTimeChart.setData(empty ? null : new LineData(sets));
        lapTimeChart.invalidate();
    }

    private static void styleLine(LineDataSet set, @ColorInt int colour, float widthDp) {
        set.setColor(colour);
        set.setLineWidth(widthDp);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setHighlightEnabled(false);
    }

    /** Per-lap rows in classification order, then any driver missing from the classification. */
    private static <T> Map<String, List<T>> orderedRows(RaceAnalysis data, Map<String, List<T>> rows) {
        Map<String, List<T>> ordered = new LinkedHashMap<>();
        if (rows == null) return ordered;
        for (RaceAnalysis.Driver d : data.drivers) {
            List<T> row = rows.get(d.driverId);
            if (row != null) ordered.put(d.driverId, row);
        }
        for (Map.Entry<String, List<T>> row : rows.entrySet()) {
            if (!ordered.containsKey(row.getKey()) && row.getValue() != null) {
                ordered.put(row.getKey(), row.getValue());
            }
        }
        return ordered;
    }

    // ── Pit stops ─────────────────────────────────────────────────────────────

    private void renderPitStops(RaceAnalysis data) {
        pitStops.removeAllViews();
        if (data.year < PIT_DATA_FIRST_SEASON) {
            tvPitNote.setText(R.string.analysis_pit_from_2011);
            return;
        }
        if (!data.pitDataAvailable || data.pitStops.isEmpty()) {
            tvPitNote.setText(R.string.analysis_no_pit_stops);
            return;
        }
        tvPitNote.setText(data.durationNote != null && !data.durationNote.isEmpty()
                ? data.durationNote : context.getString(R.string.pit_lane_note));

        RaceAnalysis.Stop fastest = null;
        Map<String, List<RaceAnalysis.Stop>> byDriver = new HashMap<>();
        for (RaceAnalysis.Stop stop : data.pitStops) {
            if (stop.driverId == null) continue;
            List<RaceAnalysis.Stop> list = byDriver.get(stop.driverId);
            if (list == null) byDriver.put(stop.driverId, list = new ArrayList<>());
            list.add(stop);
            if (stop.durationMs != null && (fastest == null || stop.durationMs < fastest.durationMs)) {
                fastest = stop;
            }
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        for (RaceAnalysis.Driver d : data.drivers) {
            List<RaceAnalysis.Stop> stops = byDriver.get(d.driverId);
            if (stops == null) continue;
            Collections.sort(stops, (a, b) -> Integer.compare(
                    a.stop != null ? a.stop : 0, b.stop != null ? b.stop : 0));
            for (int i = 0; i < stops.size(); i++) {
                pitStops.addView(pitStopRow(inflater, d, stops.get(i), i == 0, stops.get(i) == fastest));
            }
        }
    }

    /** "22.3s" under a minute, "1:02.3" (red flag stops) otherwise, "–" when unknown. */
    public static String durationText(Context context, @Nullable Long ms) {
        if (ms == null) return context.getString(R.string.weather_unknown);
        String formatted = LapTimes.formatSeconds(ms);
        return formatted.contains(":") ? formatted : context.getString(R.string.analysis_seconds, formatted);
    }

    private View pitStopRow(LayoutInflater inflater, RaceAnalysis.Driver driver,
                            RaceAnalysis.Stop stop, boolean first, boolean fastest) {
        View row = inflater.inflate(R.layout.item_analysis_pit_stop, pitStops, false);
        if (first) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) row.getLayoutParams();
            lp.topMargin = context.getResources().getDimensionPixelSize(R.dimen.spacing_sm);
            row.findViewById(R.id.view_pit_team).setBackgroundColor(colourOf(driver.driverId));
            ((TextView) row.findViewById(R.id.tv_pit_driver)).setText(driver.label());
        }
        int number = stop.stop != null ? stop.stop : 0;
        ((TextView) row.findViewById(R.id.tv_pit_stop)).setText(stop.lap != null
                ? context.getString(R.string.analysis_stop_lap, number, stop.lap)
                : context.getString(R.string.analysis_stop, number));
        ((TextView) row.findViewById(R.id.tv_pit_duration)).setText(durationText(context, stop.durationMs));
        row.findViewById(R.id.tv_pit_fastest).setVisibility(fastest ? View.VISIBLE : View.GONE);
        return row;
    }
}
