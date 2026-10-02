package com.f1stats.ui.charts;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.TextView;

import com.f1stats.R;
import com.f1stats.util.LapTimes;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

/** Tap marker for lap time entries: x = lap, y = ms, data = the driver's label. */
@SuppressLint("ViewConstructor")
public class LapTimeMarker extends MarkerView {

    private final TextView text;

    public LapTimeMarker(Context context) {
        super(context, R.layout.view_chart_marker);
        text = findViewById(R.id.tv_chart_marker);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        Object driver = e.getData();
        text.setText(getContext().getString(R.string.analysis_marker, Math.round(e.getX()),
                driver != null ? driver.toString() : "", LapTimes.formatLapTime((long) e.getY())));
        super.refreshContent(e, highlight);
    }

    /** Centred above the point. */
    @Override
    public MPPointF getOffset() {
        return new MPPointF(-getWidth() / 2f, -getHeight() - getResources().getDisplayMetrics().density * 6);
    }
}
