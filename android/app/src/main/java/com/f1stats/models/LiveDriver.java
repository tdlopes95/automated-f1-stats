package com.f1stats.models;

import com.google.gson.annotations.SerializedName;

public class LiveDriver {

    @SerializedName("driver_number")
    private int driverNumber;

    @SerializedName("name_acronym")
    private String nameAcronym;         // "VER", "HAM", etc.

    @SerializedName("full_name")
    private String fullName;

    @SerializedName("team_name")
    private String teamName;

    @SerializedName("team_colour")
    private String teamColour;          // hex e.g. "3671C6" — use for UI accents

    @SerializedName("position")
    private int position;

    @SerializedName("gap_to_leader")
    private String gapToLeader;         // e.g. "+5.234" or "1 LAP"

    @SerializedName("interval")
    private String interval;            // gap to car ahead

    @SerializedName("last_lap_duration")
    private Double lastLapDuration;     // seconds e.g. 92.456

    @SerializedName("current_compound")
    private String currentCompound;     // "SOFT", "MEDIUM", "HARD", "INTER", "WET"

    @SerializedName("tyre_age")
    private Integer tyreAge;            // laps on current tyre

    @SerializedName("pit_stops")
    private int pitStops;

    @SerializedName("last_updated")
    private String lastUpdated;

    // ── Getters ───────────────────────────────────────────────────────────────
    public int getDriverNumber() { return driverNumber; }
    public String getNameAcronym() { return nameAcronym; }
    public String getFullName() { return fullName; }
    public String getTeamName() { return teamName; }
    public String getTeamColour() { return teamColour; }
    public int getPosition() { return position; }
    public String getGapToLeader() { return gapToLeader; }
    public String getInterval() { return interval; }
    public Double getLastLapDuration() { return lastLapDuration; }
    public String getCurrentCompound() { return currentCompound; }
    public Integer getTyreAge() { return tyreAge; }
    public int getPitStops() { return pitStops; }
    public String getLastUpdated() { return lastUpdated; }

    // ── Helpers ───────────────────────────────────────────────────────────────
    public String getFormattedLastLap() {
        if (lastLapDuration == null) return "--:--.---";
        int minutes = (int) (lastLapDuration / 60);
        double seconds = lastLapDuration % 60;
        return String.format("%d:%06.3f", minutes, seconds);
    }

    public String getDisplayGap() {
        if (position == 1) return "LEADER";
        return gapToLeader != null ? gapToLeader : "--";
    }

    public String getDisplayInterval() {
        if (position == 1) return "---";
        return interval != null ? interval : "--";
    }

    // Returns the colour with # prefix for Android Color.parseColor()
    public String getTeamColourHex() {
        if (teamColour == null) return "#FFFFFF";
        return teamColour.startsWith("#") ? teamColour : "#" + teamColour;
    }

    // Tyre compound initial for display: S / M / H / I / W
    public String getCompoundShort() {
        if (currentCompound == null) return "?";
        switch (currentCompound.toUpperCase()) {
            case "SOFT":        return "S";
            case "MEDIUM":      return "M";
            case "HARD":        return "H";
            case "INTERMEDIATE":
            case "INTER":       return "I";
            case "WET":         return "W";
            default:            return "?";
        }
    }

    // Tyre compound colour (classic F1 colours)
    public String getCompoundColour() {
        if (currentCompound == null) return "#FFFFFF";
        switch (currentCompound.toUpperCase()) {
            case "SOFT":        return "#FF3333";   // red
            case "MEDIUM":      return "#FFD700";   // yellow
            case "HARD":        return "#FFFFFF";   // white
            case "INTERMEDIATE":
            case "INTER":       return "#39B54A";   // green
            case "WET":         return "#0067FF";   // blue
            default:            return "#FFFFFF";
        }
    }
}