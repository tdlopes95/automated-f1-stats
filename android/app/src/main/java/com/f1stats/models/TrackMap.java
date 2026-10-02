package com.f1stats.models;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * GET /track-map/{circuit_id}: circuit outline generated from OpenF1 position data.
 * Coordinates sit in a 1000x1000 box with Y pointing down; point 0 is the start/finish line.
 */
public class TrackMap {
    @SerializedName("circuit_id")    public String circuitId;
    @SerializedName("circuit_name")  public String circuitName;
    /** [x, y] pairs. */
    @SerializedName("points")        public List<float[]> points = new ArrayList<>();
    /** Indices into points: the end of S1 and the end of S2. */
    @SerializedName("sector_breaks") public List<Integer> sectorBreaks = new ArrayList<>();
    @SerializedName("corners")       public List<Corner> corners = new ArrayList<>();
    @SerializedName("attribution")   public String attribution;

    public static class Corner {
        @SerializedName("number") public int number;
        @SerializedName("letter") public String letter;
        @SerializedName("x")      public float x;
        @SerializedName("y")      public float y;
        /** Label direction in screen degrees from +x, clockwise. Null when unknown. */
        @SerializedName("angle")  public Float angle;

        public String label() {
            return letter != null ? number + letter : String.valueOf(number);
        }
    }
}
