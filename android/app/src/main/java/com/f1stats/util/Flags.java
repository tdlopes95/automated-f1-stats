package com.f1stats.util;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fallback country flags (flagcdn.com) for races without a usable OpenF1 meeting flag.
 * Covers every country that has hosted a World Championship Grand Prix, in both
 * Jolpica ("UK", "USA", "UAE") and OpenF1 spellings.
 */
public final class Flags {

    private static final String URL_FORMAT = "https://flagcdn.com/w80/%s.png";

    private static final Map<String, String> ISO = new HashMap<>();
    static {
        ISO.put("argentina", "ar");
        ISO.put("australia", "au");
        ISO.put("austria", "at");
        ISO.put("azerbaijan", "az");
        ISO.put("bahrain", "bh");
        ISO.put("belgium", "be");
        ISO.put("brazil", "br");
        ISO.put("canada", "ca");
        ISO.put("china", "cn");
        ISO.put("france", "fr");
        ISO.put("germany", "de");
        ISO.put("hungary", "hu");
        ISO.put("india", "in");
        ISO.put("italy", "it");
        ISO.put("japan", "jp");
        ISO.put("korea", "kr");
        ISO.put("south korea", "kr");
        ISO.put("malaysia", "my");
        ISO.put("mexico", "mx");
        ISO.put("monaco", "mc");
        ISO.put("morocco", "ma");
        ISO.put("netherlands", "nl");
        ISO.put("portugal", "pt");
        ISO.put("qatar", "qa");
        ISO.put("russia", "ru");
        ISO.put("saudi arabia", "sa");
        ISO.put("singapore", "sg");
        ISO.put("south africa", "za");
        ISO.put("spain", "es");
        ISO.put("sweden", "se");
        ISO.put("switzerland", "ch");
        ISO.put("turkey", "tr");
        ISO.put("türkiye", "tr");
        ISO.put("uae", "ae");
        ISO.put("united arab emirates", "ae");
        ISO.put("abu dhabi", "ae");
        ISO.put("uk", "gb");
        ISO.put("united kingdom", "gb");
        ISO.put("great britain", "gb");
        ISO.put("usa", "us");
        ISO.put("united states", "us");
        ISO.put("united states of america", "us");
    }

    private Flags() {}

    /** ISO 3166-1 alpha-2 code (lowercase) for a country name, or null if unknown. */
    @Nullable
    public static String isoCode(@Nullable String country) {
        if (country == null) return null;
        return ISO.get(country.trim().toLowerCase(Locale.ROOT));
    }

    /** flagcdn.com image URL for a country name, or null if unknown. */
    @Nullable
    public static String urlFor(@Nullable String country) {
        String iso = isoCode(country);
        return iso != null ? String.format(Locale.ROOT, URL_FORMAT, iso) : null;
    }
}
