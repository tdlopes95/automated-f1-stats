package com.f1stats.models;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /race-analysis/{year}/{round}: lap-by-lap positions and lap times (1996 onwards)
 * and pit stops (2011 onwards). Per-lap lists are indexed by lap - 1; null = no timing.
 */
public class RaceAnalysis {
    @SerializedName("year")               public int year;
    @SerializedName("round")              public int round;
    @SerializedName("race_name")          public String raceName;
    @SerializedName("total_laps")         public int totalLaps;
    /** Classification order. */
    @SerializedName("drivers")            public List<Driver> drivers = new ArrayList<>();
    @SerializedName("positions")          public Map<String, List<Integer>> positions = new HashMap<>();
    @SerializedName("lap_times_ms")       public Map<String, List<Long>> lapTimesMs = new HashMap<>();
    @SerializedName("pit_stops")          public List<Stop> pitStops = new ArrayList<>();
    @SerializedName("pit_data_available") public boolean pitDataAvailable;
    @SerializedName("laps_available")     public boolean lapsAvailable;
    @SerializedName("duration_note")      public String durationNote;

    public static class Driver {
        @SerializedName("driver_id")        public String driverId;
        @SerializedName("code")             public String code;
        @SerializedName("name")             public String name;
        @SerializedName("constructor_id")   public String constructorId;
        @SerializedName("constructor_name") public String constructorName;
        /** 0 = pit lane start. */
        @SerializedName("grid")             public Integer grid;
        @SerializedName("final_position")   public Integer finalPosition;
        @SerializedName("status")           public String status;

        /** Three-letter code, or the surname when the driver predates codes. */
        public String label() {
            if (code != null && !code.isEmpty()) return code;
            if (name == null || name.isEmpty()) return driverId;
            int space = name.lastIndexOf(' ');
            return space >= 0 ? name.substring(space + 1) : name;
        }
    }

    public static class Stop {
        @SerializedName("driver_id")   public String driverId;
        @SerializedName("stop")        public Integer stop;
        @SerializedName("lap")         public Integer lap;
        /** Pit-lane time (entry to exit), not the stationary time. */
        @SerializedName("duration_ms") public Long durationMs;
    }
}
