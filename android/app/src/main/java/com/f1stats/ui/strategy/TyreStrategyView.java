package com.f1stats.ui.strategy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TyreStrategyView extends View {

    private final Paint paint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Map<String, Object>> stints = new ArrayList<>();
    private int totalLaps = 1;

    public TyreStrategyView(Context context) {
        super(context);
        initTextPaint();
    }

    public TyreStrategyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initTextPaint();
    }

    private void initTextPaint() {
        float density = getContext().getResources().getDisplayMetrics().density;
        textPaint.setTextSize(10 * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setStints(List<Map<String, Object>> stints, int totalLaps) {
        this.stints   = stints != null ? stints : new ArrayList<>();
        this.totalLaps = totalLaps > 0 ? totalLaps : 1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (stints.isEmpty()) return;

        float density = getContext().getResources().getDisplayMetrics().density;
        float gap     = 1f * density;
        float radius  = 3f * density;
        float w       = getWidth();
        float h       = getHeight();
        int   n       = stints.size();

        for (int i = 0; i < n; i++) {
            Map<String, Object> stint  = stints.get(i);
            String compound = strVal(stint.get("compound"), "UNKNOWN");
            int lapStart    = intVal(stint.get("lap_start"), 1);
            int lapEnd      = intVal(stint.get("lap_end"), lapStart);

            float startFrac = (float) (lapStart - 1) / totalLaps;
            float endFrac   = (float) lapEnd        / totalLaps;

            float left  = startFrac * w + (i > 0 ? gap / 2f : 0f);
            float right = endFrac   * w - (i < n - 1 ? gap / 2f : 0f);

            paint.setColor(compoundColor(compound));

            float tl = (i == 0) ? radius : 0f;
            float tr = (i == n - 1) ? radius : 0f;
            RectF rect = new RectF(left, 0, right, h);
            Path  path = new Path();
            path.addRoundRect(rect, new float[]{tl, tl, tr, tr, tr, tr, tl, tl}, Path.Direction.CW);
            canvas.drawPath(path, paint);

            float segW = right - left;
            if (segW >= 20f * density) {
                textPaint.setColor(textColorFor(compound));
                float cx = left + segW / 2f;
                float cy = h / 2f + textPaint.getTextSize() / 3f;
                canvas.drawText(compoundAbbr(compound), cx, cy, textPaint);
            }
        }
    }

    static int compoundColor(String compound) {
        if (compound == null) return Color.GRAY;
        switch (compound.toUpperCase()) {
            case "SOFT":         return Color.parseColor("#FF3333");
            case "MEDIUM":       return Color.parseColor("#FFD700");
            case "HARD":         return Color.WHITE;
            case "INTERMEDIATE": return Color.parseColor("#39B54A");
            case "WET":          return Color.parseColor("#3399FF");
            default:             return Color.GRAY;
        }
    }

    static String compoundAbbr(String compound) {
        if (compound == null) return "?";
        switch (compound.toUpperCase()) {
            case "SOFT":         return "S";
            case "MEDIUM":       return "M";
            case "HARD":         return "H";
            case "INTERMEDIATE": return "I";
            case "WET":          return "W";
            default:             return "?";
        }
    }

    private static int textColorFor(String compound) {
        if ("SOFT".equalsIgnoreCase(compound)) return Color.WHITE;
        return Color.parseColor("#1A1A1A");
    }

    private static String strVal(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }

    private static int intVal(Object v, int fallback) {
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return fallback; }
    }
}
