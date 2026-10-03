package com.f1stats.api;

import androidx.annotation.NonNull;

import com.f1stats.util.DebugLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug-only tally of backend requests: per normalised path (ids and years replaced, e.g.
 * /results/{y}/{r}) and per full URL, so repeated requests stand out. Logged as REQ_STATS.
 */
public final class RequestStats {

    private RequestStats() {}

    public static final String TAG = "REQ_STATS";

    private static final Map<String, Integer> byPath = new HashMap<>();
    private static final Map<String, Integer> byUrl = new HashMap<>();
    private static int total;

    public static synchronized void record(@NonNull String path, @NonNull String url) {
        total++;
        byPath.merge(normalize(path), 1, Integer::sum);
        byUrl.merge(url, 1, Integer::sum);
    }

    /**
     * Replaces the variable segments of a backend path: years with {y}, the number after a
     * year with {r} (round), other numbers with {n} (session keys) and circuit ids with {id}.
     */
    @NonNull
    public static String normalize(@NonNull String path) {
        String[] segments = path.split("/");
        StringBuilder out = new StringBuilder();
        String previous = "";
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            String replaced;
            if (segment.matches("(19|20)\\d\\d")) {
                replaced = "{y}";
            } else if (segment.matches("\\d+")) {
                replaced = previous.equals("{y}") ? "{r}" : "{n}";
            } else if (previous.equals("circuit") || previous.equals("track-map")) {
                replaced = "{id}";
            } else {
                replaced = segment;
            }
            out.append('/').append(replaced);
            previous = replaced;
        }
        return out.length() > 0 ? out.toString() : "/";
    }

    /** Total, per-path counts (most requested first) and every URL requested more than once. */
    @NonNull
    public static synchronized String summary() {
        StringBuilder sb = new StringBuilder();
        int duplicates = 0;
        for (int count : byUrl.values()) duplicates += Math.max(0, count - 1);
        sb.append("total=").append(total)
          .append(" uniqueUrls=").append(byUrl.size())
          .append(" duplicates=").append(duplicates);
        for (Map.Entry<String, Integer> e : sorted(byPath)) {
            sb.append("\n  ").append(e.getValue()).append("  ").append(e.getKey());
        }
        if (duplicates > 0) {
            sb.append("\n duplicate URLs:");
            for (Map.Entry<String, Integer> e : sorted(byUrl)) {
                if (e.getValue() > 1) sb.append("\n  ").append(e.getValue()).append("x ").append(e.getKey());
            }
        }
        return sb.toString();
    }

    /** Logs {@link #summary()} under {@link #TAG}; a no-op in release builds. */
    public static void log(@NonNull String reason) {
        DebugLog.d(TAG, reason + ": " + summary());
    }

    static synchronized void clear() {
        byPath.clear();
        byUrl.clear();
        total = 0;
    }

    private static List<Map.Entry<String, Integer>> sorted(Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        return entries;
    }
}
