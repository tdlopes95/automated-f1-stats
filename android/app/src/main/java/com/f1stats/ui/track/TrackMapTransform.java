package com.f1stats.ui.track;

import java.util.List;

/**
 * Fits track coordinates (1000x1000 box, Y down) into a view: one uniform scale so the
 * aspect ratio is kept, and offsets that centre the fitted bounds inside the padding.
 * Plain Java so it can be tested on the JVM.
 */
public final class TrackMapTransform {

    public final float scale;
    public final float offsetX;
    public final float offsetY;

    private TrackMapTransform(float scale, float offsetX, float offsetY) {
        this.scale = scale;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    /** Maps the box [minX, maxX] x [minY, maxY] into viewW x viewH, leaving padding on every side. */
    public static TrackMapTransform fit(float minX, float minY, float maxX, float maxY,
                                        float viewW, float viewH, float padding) {
        float boundsW = Math.max(maxX - minX, 1f);
        float boundsH = Math.max(maxY - minY, 1f);
        float availW = Math.max(viewW - 2 * padding, 1f);
        float availH = Math.max(viewH - 2 * padding, 1f);
        float scale = Math.min(availW / boundsW, availH / boundsH);
        float offsetX = (viewW - boundsW * scale) / 2f - minX * scale;
        float offsetY = (viewH - boundsH * scale) / 2f - minY * scale;
        return new TrackMapTransform(scale, offsetX, offsetY);
    }

    /** Fits the bounding box of the points, so a long thin circuit uses the whole view. */
    public static TrackMapTransform fitPoints(List<float[]> points, float viewW, float viewH, float padding) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] p : points) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        if (points.isEmpty()) {
            minX = minY = 0f;
            maxX = maxY = 1000f;
        }
        return fit(minX, minY, maxX, maxY, viewW, viewH, padding);
    }

    public float x(float dataX) {
        return dataX * scale + offsetX;
    }

    public float y(float dataY) {
        return dataY * scale + offsetY;
    }
}
