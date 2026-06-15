package com.f1stats.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cached_meetings")
public class CachedMeeting {
    @PrimaryKey
    public int meetingKey;
    public int year;
    public String meetingName;
    public String location;
    public String countryName;
    public String countryFlagUrl;
    public String circuitShortName;
    public String circuitType;
    public String circuitImageUrl;
    public String gmtOffset;
    public long fetchedAt;
}
