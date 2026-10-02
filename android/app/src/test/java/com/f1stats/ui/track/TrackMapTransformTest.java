package com.f1stats.ui.track;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TrackMapTransformTest {

    private static final float EPS = 1e-3f;

    @Test
    public void squareBoxInWideViewIsLimitedByHeightAndCentred() {
        TrackMapTransform t = TrackMapTransform.fit(0, 0, 1000, 1000, 400, 200, 10);
        assertEquals(0.18f, t.scale, EPS);             // (200 - 2*10) / 1000
        assertEquals(200f, (t.x(0) + t.x(1000)) / 2, EPS);
        assertEquals(100f, (t.y(0) + t.y(1000)) / 2, EPS);
        assertEquals(10f, t.y(0), EPS);                 // the tight axis sits on the padding
        assertEquals(190f, t.y(1000), EPS);
    }

    @Test
    public void squareBoxInTallViewIsLimitedByWidthAndCentred() {
        TrackMapTransform t = TrackMapTransform.fit(0, 0, 1000, 1000, 200, 500, 20);
        assertEquals(0.16f, t.scale, EPS);
        assertEquals(20f, t.x(0), EPS);
        assertEquals(180f, t.x(1000), EPS);
        assertEquals(250f, (t.y(0) + t.y(1000)) / 2, EPS);
    }

    @Test
    public void aspectRatioIsPreserved() {
        // A 600x300 outline keeps its 2:1 shape whatever the view proportions
        TrackMapTransform t = TrackMapTransform.fit(200, 350, 800, 650, 300, 300, 0);
        float w = t.x(800) - t.x(200);
        float h = t.y(650) - t.y(350);
        assertEquals(2f, w / h, EPS);
        assertEquals(300f, w, EPS);
        assertEquals(150f, (t.y(350) + t.y(650)) / 2, EPS);
        assertEquals(150f, (t.x(200) + t.x(800)) / 2, EPS);
    }

    @Test
    public void fitPointsUsesTheOutlineBounds() {
        List<float[]> points = Arrays.asList(
                new float[] {100, 400}, new float[] {900, 400},
                new float[] {900, 600}, new float[] {100, 600});
        TrackMapTransform t = TrackMapTransform.fitPoints(points, 820, 400, 10);
        assertEquals(1f, t.scale, EPS);                 // 800 wide into 800 available
        assertEquals(10f, t.x(100), EPS);
        assertEquals(810f, t.x(900), EPS);
        assertEquals(200f, (t.y(400) + t.y(600)) / 2, EPS);
    }

    @Test
    public void emptyPointsFallBackToTheFullBox() {
        TrackMapTransform t = TrackMapTransform.fitPoints(Collections.emptyList(), 100, 100, 0);
        assertEquals(0.1f, t.scale, EPS);
        assertEquals(0f, t.x(0), EPS);
    }
}
