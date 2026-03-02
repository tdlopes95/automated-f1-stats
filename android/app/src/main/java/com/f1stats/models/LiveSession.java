package com.f1stats.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class LiveSession {

    @SerializedName("session_key")
    private int sessionKey;

    @SerializedName("session_name")
    private String sessionName;         // "Race", "Qualifying", etc.

    @SerializedName("session_type")
    private String sessionType;

    @SerializedName("is_live")
    private boolean isLive;

    @SerializedName("latest_flag")
    private String latestFlag;          // "GREEN", "YELLOW", "RED", "SC", "VSC"

    @SerializedName("safety_car_active")
    private boolean safetyCarActive;

    @SerializedName("vsc_active")
    private boolean vscActive;

    @SerializedName("drivers")
    private List<LiveDriver> drivers;

    @SerializedName("weather")
    private Weather weather;

    @SerializedName("last_updated")
    private String lastUpdated;

    // ── Nested: Weather ───────────────────────────────────────────────────────
    public static class Weather {
        @SerializedName("air_temperature")
        private Double airTemperature;

        @SerializedName("track_temperature")
        private Double trackTemperature;

        @SerializedName("humidity")
        private Double humidity;

        @SerializedName("rainfall")
        private Boolean rainfall;

        @SerializedName("wind_speed")
        private Double windSpeed;

        public Double getAirTemperature() { return airTemperature; }
        public Double getTrackTemperature() { return trackTemperature; }
        public Double getHumidity() { return humidity; }
        public Boolean getRainfall() { return rainfall; }
        public Double getWindSpeed() { return windSpeed; }

        public String getDisplayAirTemp() {
            return airTemperature != null ? String.format("%.1f°C", airTemperature) : "--";
        }

        public String getDisplayTrackTemp() {
            return trackTemperature != null ? String.format("%.1f°C", trackTemperature) : "--";
        }

        public boolean isRaining() {
            return rainfall != null && rainfall;
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public int getSessionKey() { return sessionKey; }
    public String getSessionName() { return sessionName; }
    public String getSessionType() { return sessionType; }
    public boolean isLive() { return isLive; }
    public String getLatestFlag() { return latestFlag; }
    public boolean isSafetyCarActive() { return safetyCarActive; }
    public boolean isVscActive() { return vscActive; }
    public List<LiveDriver> getDrivers() { return drivers; }
    public Weather getWeather() { return weather; }
    public String getLastUpdated() { return lastUpdated; }

    // ── Helpers ───────────────────────────────────────────────────────────────
    public String getSessionStatus() {
        if (safetyCarActive) return "SAFETY CAR";
        if (vscActive)       return "VIRTUAL SC";
        if (latestFlag != null) {
            switch (latestFlag.toUpperCase()) {
                case "RED":    return "RED FLAG";
                case "YELLOW": return "YELLOW FLAG";
                case "GREEN":  return "GREEN FLAG";
                default:       return latestFlag;
            }
        }
        return isLive ? "LIVE" : "SESSION ENDED";
    }

    // Status colour for the flag banner
    public String getStatusColour() {
        if (safetyCarActive || vscActive) return "#FFA500"; // orange
        if (latestFlag != null) {
            switch (latestFlag.toUpperCase()) {
                case "RED":    return "#FF0000";
                case "YELLOW": return "#FFD700";
                case "GREEN":  return "#39B54A";
            }
        }
        return "#39B54A"; // default green
    }
}