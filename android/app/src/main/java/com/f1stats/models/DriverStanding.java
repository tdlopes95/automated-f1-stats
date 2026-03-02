package com.f1stats.models;

import com.google.gson.annotations.SerializedName;

public class DriverStanding {

    @SerializedName("position")
    private String position;

    @SerializedName("positionText")
    private String positionText;

    @SerializedName("points")
    private String points;

    @SerializedName("wins")
    private String wins;

    @SerializedName("Driver")
    private RaceResult.Driver driver;

    @SerializedName("Constructors")
    private java.util.List<RaceResult.Constructor> constructors;

    @SerializedName("gap_to_second")
    private double gapToSecond;

    public double getGapToSecond() { return gapToSecond; }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getPosition() { return position; }
    public String getPositionText() { return positionText; }
    public String getPoints() { return points; }
    public String getWins() { return wins; }
    public RaceResult.Driver getDriver() { return driver; }
    public java.util.List<RaceResult.Constructor> getConstructors() { return constructors; }

    // ── Helpers ───────────────────────────────────────────────────────────────
    public String getTeamName() {
        if (constructors != null && !constructors.isEmpty()) {
            return constructors.get(0).getName();
        }
        return "N/A";
    }

}