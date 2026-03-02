package com.f1stats.models;

import com.google.gson.annotations.SerializedName;

public class ConstructorStanding {

    @SerializedName("position")
    private String position;

    @SerializedName("positionText")
    private String positionText;

    @SerializedName("points")
    private String points;

    @SerializedName("wins")
    private String wins;

    @SerializedName("Constructor")
    private RaceResult.Constructor constructor;

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getPosition() { return position; }
    public String getPositionText() { return positionText; }
    public String getPoints() { return points; }
    public String getWins() { return wins; }
    public RaceResult.Constructor getConstructor() { return constructor; }
}