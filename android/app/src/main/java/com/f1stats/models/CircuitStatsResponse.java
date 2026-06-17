package com.f1stats.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class CircuitStatsResponse {
    @SerializedName("circuitId")    public String circuitId;
    @SerializedName("circuitName")  public String circuitName;
    @SerializedName("locality")     public String locality;
    @SerializedName("country")      public String country;
    @SerializedName("totalRaces")   public int totalRaces;
    @SerializedName("firstGPYear")  public int firstGPYear;
    @SerializedName("lastGPYear")   public int lastGPYear;
    @SerializedName("mostWins")             public DriverStat mostWins;
    @SerializedName("mostPoles")            public DriverStat mostPoles;
    @SerializedName("mostConstructorWins")  public ConstructorStat mostConstructorWins;
    @SerializedName("lapRecord")            public LapRecord lapRecord;

    public static class DriverStat {
        public String driverId;
        public String name;
        public int count;
        public List<Integer> years;
    }

    public static class ConstructorStat {
        public String constructorId;
        public String name;
        public int count;
    }

    public static class LapRecord {
        public String driverId;
        public String name;
        public String time;
        public int year;
    }
}
