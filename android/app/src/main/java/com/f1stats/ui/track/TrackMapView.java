package com.f1stats.ui.track;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.f1stats.R;
import com.f1stats.models.TrackMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a generated circuit map: sector-coloured outline, start/finish line, a direction
 * chevron and numbered corners. Pinch to zoom, drag to pan, double-tap to reset.
 */
public class TrackMapView extends View {

    private static final float MAX_ZOOM = 5f;

    private final List<float[]> points = new ArrayList<>();
    private final List<TrackMap.Corner> corners = new ArrayList<>();
    /** Outline split at the sector breaks; a single path when there are no usable breaks. */
    private final List<Path> sectorPaths = new ArrayList<>();
    private final Path outline = new Path();
    private final Path scratch = new Path();
    private final Matrix matrix = new Matrix();
    private TrackMapTransform fit;
    private float centroidX, centroidY;
    private boolean showSectors = true;

    private float userScale = 1f, userTx = 0f, userTy = 0f;

    private final Paint underlayPaint = strokePaint();
    private final Paint trackPaint = strokePaint();
    private final Paint chevronPaint = strokePaint();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] sectorColors;
    private final int neutralColor, cornerBg, cornerText, checkLight, checkDark;

    private final float trackWidth, padding, labelOffset, labelMinRadius, checkSquare, chevronSize;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    public TrackMapView(Context context) {
        this(context, null);
    }

    public TrackMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        trackWidth     = dp(10);
        padding        = dp(28);
        labelOffset    = dp(17);
        labelMinRadius = dp(8);
        checkSquare    = dp(4);
        chevronSize    = dp(3.5f);

        sectorColors = new int[] {
            ContextCompat.getColor(context, R.color.sector_1),
            ContextCompat.getColor(context, R.color.sector_2),
            ContextCompat.getColor(context, R.color.sector_3)
        };
        neutralColor = ContextCompat.getColor(context, R.color.track_map_neutral);
        cornerBg     = ContextCompat.getColor(context, R.color.track_map_corner_bg);
        cornerText   = ContextCompat.getColor(context, R.color.track_map_corner_text);
        checkLight   = ContextCompat.getColor(context, R.color.white);
        checkDark    = ContextCompat.getColor(context, R.color.black);

        underlayPaint.setStrokeWidth(trackWidth + dp(4));
        underlayPaint.setColor(ContextCompat.getColor(context, R.color.track_map_underlay));
        trackPaint.setStrokeWidth(trackWidth);
        chevronPaint.setStrokeWidth(dp(2));
        chevronPaint.setColor(ContextCompat.getColor(context, R.color.track_map_chevron));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
        labelPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 9, getResources().getDisplayMetrics()));
        labelPaint.setColor(cornerText);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                zoomBy(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float dx, float dy) {
                userTx -= dx;
                userTy -= dy;
                clampPan();
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                resetZoom();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                return performClick();
            }
        });
    }

    public void setTrackMap(TrackMap map) {
        points.clear();
        corners.clear();
        for (float[] p : map.points) {
            if (p != null && p.length >= 2) points.add(p);
        }
        if (map.corners != null) corners.addAll(map.corners);
        buildPaths(map.sectorBreaks);
        fit = null;
        resetZoom();
        requestLayout();
    }

    /** True when the map has two valid sector breaks, so sector colours mean something. */
    public boolean hasSectors() {
        return sectorPaths.size() == 3;
    }

    public void setShowSectors(boolean show) {
        showSectors = show;
        invalidate();
    }

    public void resetZoom() {
        userScale = 1f;
        userTx = 0f;
        userTy = 0f;
        invalidate();
    }

    private void buildPaths(List<Integer> breaks) {
        outline.reset();
        sectorPaths.clear();
        centroidX = centroidY = 0f;
        int n = points.size();
        if (n < 2) return;
        for (int i = 0; i < n; i++) {
            float[] p = points.get(i);
            if (i == 0) outline.moveTo(p[0], p[1]); else outline.lineTo(p[0], p[1]);
            centroidX += p[0] / n;
            centroidY += p[1] / n;
        }
        outline.close();

        if (breaks == null || breaks.size() < 2) return;
        int b1 = breaks.get(0), b2 = breaks.get(1);
        if (b1 <= 0 || b2 <= b1 || b2 >= n - 1) return;
        sectorPaths.add(segment(0, b1, false));
        sectorPaths.add(segment(b1, b2, false));
        sectorPaths.add(segment(b2, n - 1, true));
    }

    private Path segment(int from, int to, boolean closeToStart) {
        Path path = new Path();
        path.moveTo(points.get(from)[0], points.get(from)[1]);
        for (int i = from + 1; i <= to; i++) path.lineTo(points.get(i)[0], points.get(i)[1]);
        if (closeToStart) path.lineTo(points.get(0)[0], points.get(0)[1]);
        return path;
    }

    // ── Gestures ────────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Keep the scroll view from stealing pinches, and drags once zoomed in
        if (event.getPointerCount() > 1 || userScale > 1f) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        boolean handled = scaleDetector.onTouchEvent(event);
        handled |= gestureDetector.onTouchEvent(event);
        return handled || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void zoomBy(float factor, float focusX, float focusY) {
        float next = Math.max(1f, Math.min(userScale * factor, MAX_ZOOM));
        float applied = next / userScale;
        userTx = focusX - (focusX - userTx) * applied;
        userTy = focusY - (focusY - userTy) * applied;
        userScale = next;
        clampPan();
        invalidate();
    }

    private void clampPan() {
        float minTx = getWidth() - getWidth() * userScale;
        float minTy = getHeight() - getHeight() * userScale;
        userTx = Math.max(minTx, Math.min(userTx, 0f));
        userTy = Math.max(minTy, Math.min(userTy, 0f));
    }

    // ── Drawing ─────────────────────────────────────────────────────────────

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fit = null;
        clampPan();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (points.size() < 2 || getWidth() == 0 || getHeight() == 0) return;
        if (fit == null) fit = TrackMapTransform.fitPoints(points, getWidth(), getHeight(), padding);

        matrix.setScale(fit.scale, fit.scale);
        matrix.postTranslate(fit.offsetX, fit.offsetY);
        matrix.postScale(userScale, userScale);
        matrix.postTranslate(userTx, userTy);

        outline.transform(matrix, scratch);
        canvas.drawPath(scratch, underlayPaint);
        if (showSectors && hasSectors()) {
            for (int i = 0; i < 3; i++) {
                sectorPaths.get(i).transform(matrix, scratch);
                trackPaint.setColor(sectorColors[i]);
                canvas.drawPath(scratch, trackPaint);
            }
        } else {
            outline.transform(matrix, scratch);
            trackPaint.setColor(neutralColor);
            canvas.drawPath(scratch, trackPaint);
        }

        drawStartLine(canvas);
        drawChevron(canvas);
        drawCorners(canvas);
    }

    /** Chequered strip across the track at point 0. */
    private void drawStartLine(Canvas canvas) {
        float[] p0 = map(points.get(0));
        float[] p1 = map(points.get(Math.min(2, points.size() - 1)));
        canvas.save();
        canvas.translate(p0[0], p0[1]);
        canvas.rotate(angleDeg(p0, p1));
        // Local axes: x along the driving direction, y across the track
        int across = 4;
        float half = across * checkSquare / 2f;
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < across; i++) {
                fillPaint.setColor((row + i) % 2 == 0 ? checkLight : checkDark);
                float left = (row - 1) * checkSquare;
                float top = -half + i * checkSquare;
                canvas.drawRect(left, top, left + checkSquare, top + checkSquare, fillPaint);
            }
        }
        canvas.restore();
    }

    /** A small arrowhead a few points past the line, pointing the way the cars go. */
    private void drawChevron(Canvas canvas) {
        int n = points.size();
        int at = Math.min(Math.max(3, n / 25), n - 2);
        if (at < 1) return;
        float[] p = map(points.get(at));
        float angle = angleDeg(map(points.get(at - 1)), map(points.get(at + 1)));
        canvas.save();
        canvas.translate(p[0], p[1]);
        canvas.rotate(angle);
        scratch.reset();
        scratch.moveTo(-chevronSize, -chevronSize);
        scratch.lineTo(0, 0);
        scratch.lineTo(-chevronSize, chevronSize);
        canvas.drawPath(scratch, chevronPaint);
        canvas.restore();
    }

    /** Numbered circles pushed outward along each corner's angle; a label that would overlap is skipped. */
    private void drawCorners(Canvas canvas) {
        List<float[]> placed = new ArrayList<>();   // x, y, radius
        Paint.FontMetrics fm = labelPaint.getFontMetrics();
        float textCentre = -(fm.ascent + fm.descent) / 2f;
        for (TrackMap.Corner corner : corners) {
            float dirX, dirY;
            if (corner.angle != null) {
                double rad = Math.toRadians(corner.angle);
                dirX = (float) Math.cos(rad);
                dirY = (float) Math.sin(rad);
            } else {
                // No angle: push away from the middle of the circuit
                dirX = corner.x - centroidX;
                dirY = corner.y - centroidY;
                float len = (float) Math.hypot(dirX, dirY);
                if (len < 1e-3f) continue;
                dirX /= len;
                dirY /= len;
            }
            float[] at = map(new float[] {corner.x, corner.y});
            String label = corner.label();
            float radius = Math.max(labelMinRadius, labelPaint.measureText(label) / 2f + dp(3));
            float cx = at[0] + dirX * labelOffset;
            float cy = at[1] + dirY * labelOffset;

            boolean overlaps = false;
            for (float[] other : placed) {
                if (Math.hypot(cx - other[0], cy - other[1]) < radius + other[2]) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) continue;
            placed.add(new float[] {cx, cy, radius});

            fillPaint.setColor(cornerBg);
            canvas.drawCircle(cx, cy, radius, fillPaint);
            canvas.drawText(label, cx, cy + textCentre, labelPaint);
        }
    }

    private float[] map(float[] dataPoint) {
        float[] out = {dataPoint[0], dataPoint[1]};
        matrix.mapPoints(out);
        return out;
    }

    private static float angleDeg(float[] from, float[] to) {
        return (float) Math.toDegrees(Math.atan2(to[1] - from[1], to[0] - from[0]));
    }

    private static Paint strokePaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        return paint;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
