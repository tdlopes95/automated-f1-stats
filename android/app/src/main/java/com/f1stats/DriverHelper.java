package com.f1stats;

import java.util.HashMap;
import java.util.Map;

public class DriverHelper {

    // Nationality → flag emoji
    private static final Map<String, String> FLAGS = new HashMap<>();

    // Country name → flag emoji (for circuit/race locations)
    private static final Map<String, String> COUNTRY_FLAGS = new HashMap<>();
    static {
        COUNTRY_FLAGS.put("Australia",      "🇦🇺");
        COUNTRY_FLAGS.put("Bahrain",        "🇧🇭");
        COUNTRY_FLAGS.put("Saudi Arabia",   "🇸🇦");
        COUNTRY_FLAGS.put("Japan",          "🇯🇵");
        COUNTRY_FLAGS.put("China",          "🇨🇳");
        COUNTRY_FLAGS.put("USA",            "🇺🇸");
        COUNTRY_FLAGS.put("United States",  "🇺🇸");
        COUNTRY_FLAGS.put("Italy",          "🇮🇹");
        COUNTRY_FLAGS.put("Monaco",         "🇲🇨");
        COUNTRY_FLAGS.put("Spain",          "🇪🇸");
        COUNTRY_FLAGS.put("Canada",         "🇨🇦");
        COUNTRY_FLAGS.put("Austria",        "🇦🇹");
        COUNTRY_FLAGS.put("United Kingdom", "🇬🇧");
        COUNTRY_FLAGS.put("UK",             "🇬🇧");
        COUNTRY_FLAGS.put("Hungary",        "🇭🇺");
        COUNTRY_FLAGS.put("Belgium",        "🇧🇪");
        COUNTRY_FLAGS.put("Netherlands",    "🇳🇱");
        COUNTRY_FLAGS.put("Singapore",      "🇸🇬");
        COUNTRY_FLAGS.put("Mexico",         "🇲🇽");
        COUNTRY_FLAGS.put("Brazil",         "🇧🇷");
        COUNTRY_FLAGS.put("Qatar",          "🇶🇦");
        COUNTRY_FLAGS.put("UAE",            "🇦🇪");
        COUNTRY_FLAGS.put("Abu Dhabi",      "🇦🇪");
        COUNTRY_FLAGS.put("Azerbaijan",     "🇦🇿");
        COUNTRY_FLAGS.put("France",         "🇫🇷");
        COUNTRY_FLAGS.put("Germany",        "🇩🇪");
        COUNTRY_FLAGS.put("Portugal",       "🇵🇹");
        COUNTRY_FLAGS.put("Turkey",         "🇹🇷");
        COUNTRY_FLAGS.put("South Korea",    "🇰🇷");
        COUNTRY_FLAGS.put("India",          "🇮🇳");
        COUNTRY_FLAGS.put("Russia",         "🇷🇺");
    }
    static {
        FLAGS.put("British",        "🇬🇧");
        FLAGS.put("Dutch",          "🇳🇱");
        FLAGS.put("Australian",     "🇦🇺");
        FLAGS.put("German",         "🇩🇪");
        FLAGS.put("Spanish",        "🇪🇸");
        FLAGS.put("French",         "🇫🇷");
        FLAGS.put("Monegasque",     "🇲🇨");
        FLAGS.put("Finnish",        "🇫🇮");
        FLAGS.put("Mexican",        "🇲🇽");
        FLAGS.put("Canadian",       "🇨🇦");
        FLAGS.put("Japanese",       "🇯🇵");
        FLAGS.put("Thai",           "🇹🇭");
        FLAGS.put("Danish",         "🇩🇰");
        FLAGS.put("Italian",        "🇮🇹");
        FLAGS.put("Brazilian",      "🇧🇷");
        FLAGS.put("American",       "🇺🇸");
        FLAGS.put("Austrian",       "🇦🇹");
        FLAGS.put("Argentine",      "🇦🇷");
        FLAGS.put("New Zealander",  "🇳🇿");
        FLAGS.put("Swiss",          "🇨🇭");
        FLAGS.put("Swedish",        "🇸🇪");
        FLAGS.put("Polish",         "🇵🇱");
        FLAGS.put("Chinese",        "🇨🇳");
        FLAGS.put("Russian",        "🇷🇺");
        FLAGS.put("Belgian",        "🇧🇪");
        FLAGS.put("Hungarian",      "🇭🇺");
        FLAGS.put("Portuguese",     "🇵🇹");
    }

    public static String getFlag(String nationality) {
        if (nationality == null) return "";
        return FLAGS.getOrDefault(nationality, "🏁");
    }

    public static String getFlagForCountry(String country) {
        if (country == null) return "";
        return COUNTRY_FLAGS.getOrDefault(country, "");
    }

    // Driver number styled display e.g. "#44"
    public static String getNumberDisplay(String number) {
        if (number == null || number.isEmpty()) return "";
        return "#" + number;
    }

    // Nationality → short country code for compact display
    public static String getShortNationality(String nationality) {
        if (nationality == null) return "";
        switch (nationality) {
            case "British":       return "GBR";
            case "Dutch":         return "NED";
            case "Australian":    return "AUS";
            case "German":        return "GER";
            case "Spanish":       return "ESP";
            case "French":        return "FRA";
            case "Monegasque":    return "MON";
            case "Finnish":       return "FIN";
            case "Mexican":       return "MEX";
            case "Canadian":      return "CAN";
            case "Japanese":      return "JPN";
            case "Thai":          return "THA";
            case "Danish":        return "DEN";
            case "Italian":       return "ITA";
            case "Brazilian":     return "BRA";
            case "American":      return "USA";
            case "Austrian":      return "AUT";
            case "Argentine":     return "ARG";
            case "New Zealander": return "NZL";
            case "Swiss":         return "SUI";
            default:              return nationality.substring(0, Math.min(3, nationality.length())).toUpperCase();
        }
    }
}