package com.f1stats.models;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** GET /circuit/{circuit_id}/pit-history: pit stop trend for recent races, oldest first. */
public class CircuitPitHistory {
    @SerializedName("circuit_id")    public String circuitId;
    @SerializedName("races")         public List<Race> races = new ArrayList<>();
    @SerializedName("duration_note") public String durationNote;

    public static class Race {
        @SerializedName("season")                 public int season;
        @SerializedName("round")                  public int round;
        @SerializedName("race_name")              public String raceName;
        /** Null when the race has no pit data. */
        @SerializedName("avg_stops_per_finisher") public Double avgStopsPerFinisher;
        @SerializedName("total_stops")            public int totalStops;
        @SerializedName("fastest_stop")           public FastestStop fastestStop;
    }

    public static class FastestStop {
        @SerializedName("driver_id")   public String driverId;
        @SerializedName("name")        public String name;
        @SerializedName("duration_ms") public long durationMs;
        @SerializedName("lap")         public Integer lap;
    }
}
