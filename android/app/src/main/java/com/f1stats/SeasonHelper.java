package com.f1stats;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class SeasonHelper {

    // F1 started in 1950 — returns list from current year down to 1950
    public static List<String> getAllSeasons() {
        List<String> seasons = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int year = currentYear; year >= 1950; year--) {
            seasons.add(String.valueOf(year));
        }
        return seasons;
    }

    public static int getCurrentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }
}