package com.f1stats.models;

import com.google.gson.annotations.SerializedName;

public class PitStop {

    @SerializedName("driver_number")
    private int driverNumber;

    @SerializedName("driver_name")
    private String driverName;

    @SerializedName("team_name")
    private String teamName;

    @SerializedName("team_colour")
    private String teamColour;

    @SerializedName("lap_number")
    private int lapNumber;

    @SerializedName("stop_duration")
    private Double stopDuration;

    @SerializedName("pit_duration")
    private Double pitDuration;

    public int getDriverNumber() { return driverNumber; }
    public String getDriverName() { return driverName; }
    public String getTeamName() { return teamName; }
    public String getTeamColourHex() {
        return teamColour != null ? teamColour : "#FFFFFF";
    }
    public int getLapNumber() { return lapNumber; }
    public Double getStopDuration() { return stopDuration; }

    public String getFormattedStopDuration() {
        if (stopDuration == null) return "--";
        // Remove trailing zeros e.g. 2.1 → "2.1s" not "2.100s"
        String formatted = String.format(java.util.Locale.getDefault(), "%.3f", stopDuration);
        formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        return formatted + "s";
    }

    public String getFormattedPitDuration() {
        if (pitDuration == null) return "--";
        return String.format(java.util.Locale.getDefault(), "%.3fs", pitDuration);
    }

    public double getRankingTime() {
        return stopDuration != null ? stopDuration : 999.0;
    }
}