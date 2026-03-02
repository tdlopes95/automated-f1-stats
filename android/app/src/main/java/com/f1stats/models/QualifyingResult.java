package com.f1stats.models;

import com.google.gson.annotations.SerializedName;

public class QualifyingResult {

    @SerializedName("position")
    private String position;

    @SerializedName("Driver")
    private RaceResult.Driver driver;

    @SerializedName("Constructor")
    private RaceResult.Constructor constructor;

    @SerializedName("Q1")
    private String q1;

    @SerializedName("Q2")
    private String q2;

    @SerializedName("Q3")
    private String q3;

    public String getPosition() { return position; }
    public RaceResult.Driver getDriver() { return driver; }
    public RaceResult.Constructor getConstructor() { return constructor; }
    public String getQ1() { return q1 != null ? q1 : "--"; }
    public String getQ2() { return q2 != null ? q2 : "--"; }
    public String getQ3() { return q3 != null ? q3 : "--"; }

    // Returns the best time across Q sessions
    public String getBestTime() {
        if (q3 != null && !q3.isEmpty()) return q3;
        if (q2 != null && !q2.isEmpty()) return q2;
        if (q1 != null && !q1.isEmpty()) return q1;
        return "--";
    }

    // Which session did they reach?
    public String getEliminatedIn() {
        if (q3 != null && !q3.isEmpty()) return "Q3";
        if (q2 != null && !q2.isEmpty()) return "Q2";
        return "Q1";
    }
}