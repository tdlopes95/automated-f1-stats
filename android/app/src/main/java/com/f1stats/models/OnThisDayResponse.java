package com.f1stats.models;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** GET /history/on-this-day: race winners from this week in past seasons, closest date first. */
public class OnThisDayResponse {
    @SerializedName("date")   public String date;
    @SerializedName("window") public int window;
    @SerializedName("items")  public List<Item> items = new ArrayList<>();

    public static class Item {
        @SerializedName("season")           public int season;
        @SerializedName("round")            public int round;
        @SerializedName("race_name")        public String raceName;
        /** YYYY-MM-DD. */
        @SerializedName("date")             public String date;
        @SerializedName("circuit_id")       public String circuitId;
        @SerializedName("circuit_name")     public String circuitName;
        @SerializedName("country")          public String country;
        @SerializedName("driver_id")        public String driverId;
        @SerializedName("driver_name")      public String driverName;
        @SerializedName("constructor_id")   public String constructorId;
        @SerializedName("constructor_name") public String constructorName;
        @SerializedName("years_ago")        public int yearsAgo;
    }
}
