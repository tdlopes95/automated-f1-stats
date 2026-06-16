package com.f1stats.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "cached_drivers", primaryKeys = {"driverId", "seasonYear"})
public class CachedDriver {
    @NonNull public String driverId = "";
    public String code;
    public String firstName;
    public String lastName;
    public String nationality;
    public String dateOfBirth;
    public String permanentNumber;
    public String headshotUrl;
    public String teamName;
    public String teamColour;
    public int seasonYear;
    public long fetchedAt;
}
