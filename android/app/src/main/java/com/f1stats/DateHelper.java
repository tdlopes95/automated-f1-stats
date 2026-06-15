package com.f1stats;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateHelper {

    private static final String ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

    public static String formatForDisplay(String isoUtcString, String outputPattern) {
        if (isoUtcString == null) return "--";
        try {
            SimpleDateFormat input = new SimpleDateFormat(ISO_PATTERN, Locale.getDefault());
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = input.parse(isoUtcString);
            if (date == null) return isoUtcString;
            SimpleDateFormat output = new SimpleDateFormat(outputPattern, Locale.getDefault());
            return output.format(date);
        } catch (Exception e) {
            return isoUtcString;
        }
    }

    public static long toMillis(String isoUtcString) {
        if (isoUtcString == null) return -1;
        try {
            SimpleDateFormat input = new SimpleDateFormat(ISO_PATTERN, Locale.getDefault());
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = input.parse(isoUtcString);
            return date != null ? date.getTime() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public static String formatShort(String isoUtcString) {
        return formatForDisplay(isoUtcString, "EEE dd MMM, HH:mm");
    }

    public static String formatFull(String isoUtcString) {
        return formatForDisplay(isoUtcString, "EEE dd MMM yyyy, HH:mm");
    }
}
