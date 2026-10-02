package com.f1stats.ui.charts;

import android.content.Context;
import android.graphics.Color;

import androidx.core.content.ContextCompat;

import com.f1stats.R;
import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;

/** Dark theme styling shared by every MPAndroidChart chart. */
public final class ChartStyle {

    private ChartStyle() {}

    /**
     * Transparent background, muted grid and axis text, no description or legend, a single
     * left Y axis, and zooming (when enabled) on the X axis only.
     */
    public static void apply(BarLineChartBase<?> chart) {
        Context context = chart.getContext();
        int grid = ContextCompat.getColor(context, R.color.chart_grid);
        int text = ContextCompat.getColor(context, R.color.chart_axis_text);

        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText(context.getString(R.string.chart_no_data));
        chart.setNoDataTextColor(text);
        chart.setScaleYEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);

        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setTextColor(text);
        x.setGridColor(grid);
        x.setAxisLineColor(grid);
        x.setDrawGridLines(false);
        x.setGranularity(1f);

        YAxis left = chart.getAxisLeft();
        left.setTextColor(text);
        left.setGridColor(grid);
        left.setDrawAxisLine(false);
        chart.getAxisRight().setEnabled(false);
    }
}
