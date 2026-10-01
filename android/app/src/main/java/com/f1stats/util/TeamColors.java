package com.f1stats.util;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.f1stats.R;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Single source of truth for team colours.
 *
 * Priority: valid OpenF1 team_colour → Jolpica constructorId → team-name keyword → neutral default.
 */
public final class TeamColors {

    private static final Map<String, Integer> BY_CONSTRUCTOR_ID = new HashMap<>();
    static {
        BY_CONSTRUCTOR_ID.put("mercedes",     R.color.team_mercedes);
        BY_CONSTRUCTOR_ID.put("ferrari",      R.color.team_ferrari);
        BY_CONSTRUCTOR_ID.put("red_bull",     R.color.team_red_bull);
        BY_CONSTRUCTOR_ID.put("mclaren",      R.color.team_mclaren);
        BY_CONSTRUCTOR_ID.put("aston_martin", R.color.team_aston_martin);
        BY_CONSTRUCTOR_ID.put("alpine",       R.color.team_alpine);
        BY_CONSTRUCTOR_ID.put("williams",     R.color.team_williams);
        BY_CONSTRUCTOR_ID.put("rb",           R.color.team_rb);
        BY_CONSTRUCTOR_ID.put("haas",         R.color.team_haas);
        BY_CONSTRUCTOR_ID.put("audi",         R.color.team_audi);
        BY_CONSTRUCTOR_ID.put("cadillac",     R.color.team_cadillac);
        BY_CONSTRUCTOR_ID.put("sauber",       R.color.team_sauber);
        BY_CONSTRUCTOR_ID.put("alphatauri",   R.color.team_alphatauri);
        BY_CONSTRUCTOR_ID.put("toro_rosso",   R.color.team_toro_rosso);
        BY_CONSTRUCTOR_ID.put("renault",      R.color.team_renault);
        BY_CONSTRUCTOR_ID.put("racing_point", R.color.team_racing_point);
        BY_CONSTRUCTOR_ID.put("force_india",  R.color.team_force_india);
        BY_CONSTRUCTOR_ID.put("lotus_f1",     R.color.team_lotus_f1);
        BY_CONSTRUCTOR_ID.put("brawn",        R.color.team_brawn);
        BY_CONSTRUCTOR_ID.put("toyota",       R.color.team_toyota);
        BY_CONSTRUCTOR_ID.put("honda",        R.color.team_honda);
        BY_CONSTRUCTOR_ID.put("bmw_sauber",   R.color.team_bmw_sauber);
    }

    // Order matters: more specific keywords first ("racing bulls" before "red bull",
    // "bmw sauber" before "sauber", "racing point" before generic matches).
    private static final String[][] NAME_KEYWORDS = {
            {"racing bulls", "rb"},
            {"rb f1",        "rb"},
            {"visa cash app","rb"},
            {"alphatauri",   "alphatauri"},
            {"alpha tauri",  "alphatauri"},
            {"toro rosso",   "toro_rosso"},
            {"red bull",     "red_bull"},
            {"bmw sauber",   "bmw_sauber"},
            {"sauber",       "sauber"},
            {"audi",         "audi"},
            {"cadillac",     "cadillac"},
            {"haas",         "haas"},
            {"mercedes",     "mercedes"},
            {"ferrari",      "ferrari"},
            {"mclaren",      "mclaren"},
            {"aston martin", "aston_martin"},
            {"alpine",       "alpine"},
            {"williams",     "williams"},
            {"racing point", "racing_point"},
            {"force india",  "force_india"},
            {"renault",      "renault"},
            {"lotus",        "lotus_f1"},
            {"brawn",        "brawn"},
            {"toyota",       "toyota"},
            {"honda",        "honda"},
    };

    private TeamColors() {}

    @ColorInt
    public static int get(Context context, @Nullable String constructorId,
                          @Nullable String teamName, @Nullable String apiColour) {
        Integer api = parseApiColour(apiColour);
        if (api != null) return api;
        return ContextCompat.getColor(context, resolveRes(constructorId, teamName));
    }

    /** Parses an OpenF1 team_colour ("3671C6" or "#3671C6"); null if missing or malformed. */
    @VisibleForTesting
    @Nullable
    @ColorInt
    static Integer parseApiColour(@Nullable String colour) {
        if (colour == null) return null;
        String hex = colour.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9A-Fa-f]{6}") && !hex.matches("[0-9A-Fa-f]{8}")) return null;
        long value = Long.parseLong(hex, 16);
        if (hex.length() == 6) value |= 0xFF000000L;
        return (int) value;
    }

    @VisibleForTesting
    @ColorRes
    static int resolveRes(@Nullable String constructorId, @Nullable String teamName) {
        if (constructorId != null) {
            Integer res = BY_CONSTRUCTOR_ID.get(constructorId.trim().toLowerCase(Locale.ROOT));
            if (res != null) return res;
        }
        if (teamName != null) {
            String name = teamName.trim().toLowerCase(Locale.ROOT);
            if (name.equals("rb")) return R.color.team_rb;
            for (String[] kw : NAME_KEYWORDS) {
                if (name.contains(kw[0])) return BY_CONSTRUCTOR_ID.get(kw[1]);
            }
        }
        return R.color.team_default;
    }
}
