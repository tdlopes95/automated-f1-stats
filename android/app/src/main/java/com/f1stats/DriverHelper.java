package com.f1stats;

import java.util.HashMap;
import java.util.Map;

public class DriverHelper {

    // Nationality → flag emoji
    private static final Map<String, String> FLAGS = new HashMap<>();
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