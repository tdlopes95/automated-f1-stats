package com.f1stats.models;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** GET /weather/{year}/{round}: race weekend forecast from Open-Meteo. Null numbers = no data. */
public class WeatherForecast {
    @SerializedName("year")           public int year;
    @SerializedName("round")          public int round;
    @SerializedName("race_name")      public String raceName;
    @SerializedName("available")      public boolean available;
    /** YYYY-MM-DD; set when the weekend is still beyond the forecast range. */
    @SerializedName("available_from") public String availableFrom;
    @SerializedName("days")           public List<Day> days = new ArrayList<>();
    @SerializedName("sessions")       public List<Session> sessions = new ArrayList<>();
    @SerializedName("attribution")    public String attribution;

    public static class Day {
        /** YYYY-MM-DD (UTC). */
        @SerializedName("date")                 public String date;
        /** WMO weather code. */
        @SerializedName("weather_code")         public Integer weatherCode;
        @SerializedName("temp_max")             public Double tempMax;
        @SerializedName("temp_min")             public Double tempMin;
        @SerializedName("rain_probability_max") public Integer rainProbabilityMax;
    }

    public static class Session {
        @SerializedName("name")             public String name;
        @SerializedName("datetime_utc")     public String datetimeUtc;
        @SerializedName("temperature")      public Double temperature;
        @SerializedName("rain_probability") public Integer rainProbability;
        @SerializedName("precipitation")    public Double precipitation;
        @SerializedName("wind_speed")       public Double windSpeed;
        @SerializedName("weather_code")     public Integer weatherCode;
    }
}
