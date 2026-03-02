package com.f1stats.models;

import com.google.gson.annotations.SerializedName;

public class RaceResult {

    @SerializedName("position")
    private String position;

    @SerializedName("number")
    private String driverNumber;

    @SerializedName("points")
    private String points;

    @SerializedName("grid")
    private String gridPosition;

    @SerializedName("laps")
    private String laps;

    @SerializedName("status")
    private String status;          // "Finished", "+1 Lap", "DNF", etc.

    @SerializedName("Time")
    private RaceTime time;

    @SerializedName("FastestLap")
    private FastestLap fastestLap;

    @SerializedName("Driver")
    private Driver driver;

    @SerializedName("Constructor")
    private Constructor constructor;

    // ── Nested: Driver ────────────────────────────────────────────────────────
    public static class Driver {
        @SerializedName("driverId")
        private String driverId;

        @SerializedName("permanentNumber")
        private String number;

        @SerializedName("code")
        private String code;            // e.g. "VER", "HAM"

        @SerializedName("givenName")
        private String firstName;

        @SerializedName("familyName")
        private String lastName;

        @SerializedName("nationality")
        private String nationality;

        public String getDriverId() { return driverId; }
        public String getNumber() { return number; }
        public String getCode() { return code; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getNationality() { return nationality; }
        public String getFullName() { return firstName + " " + lastName; }
    }

    // ── Nested: Constructor (Team) ────────────────────────────────────────────
    public static class Constructor {
        @SerializedName("constructorId")
        private String constructorId;

        @SerializedName("name")
        private String name;

        @SerializedName("nationality")
        private String nationality;

        public String getConstructorId() { return constructorId; }
        public String getName() { return name; }
        public String getNationality() { return nationality; }
    }

    // ── Nested: Race Time ─────────────────────────────────────────────────────
    public static class RaceTime {
        @SerializedName("millis")
        private String millis;

        @SerializedName("time")
        private String time;            // e.g. "1:32:05.637"

        public String getMillis() { return millis; }
        public String getTime() { return time; }
    }

    // ── Nested: Fastest Lap ───────────────────────────────────────────────────
    public static class FastestLap {
        @SerializedName("rank")
        private String rank;            // "1" = fastest lap award

        @SerializedName("lap")
        private String lap;

        @SerializedName("Time")
        private RaceTime time;

        @SerializedName("AverageSpeed")
        private AverageSpeed averageSpeed;

        public String getRank() { return rank; }
        public String getLap() { return lap; }
        public RaceTime getTime() { return time; }
        public AverageSpeed getAverageSpeed() { return averageSpeed; }

        public static class AverageSpeed {
            @SerializedName("units")
            private String units;

            @SerializedName("speed")
            private String speed;

            public String getUnits() { return units; }
            public String getSpeed() { return speed; }
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getPosition() { return position; }
    public String getDriverNumber() { return driverNumber; }
    public String getPoints() { return points; }
    public String getGridPosition() { return gridPosition; }
    public String getLaps() { return laps; }
    public String getStatus() { return status; }
    public RaceTime getTime() { return time; }
    public FastestLap getFastestLap() { return fastestLap; }
    public Driver getDriver() { return driver; }
    public Constructor getConstructor() { return constructor; }

    // ── Helpers ───────────────────────────────────────────────────────────────
    public boolean hasFastestLap() {
        return fastestLap != null && "1".equals(fastestLap.getRank());
    }

    public boolean isFinished() {
        return status != null && status.equals("Finished");
    }

    public String getDisplayTime() {
        if (time != null && time.getTime() != null) return time.getTime();
        return status != null ? status : "N/A";
    }
}