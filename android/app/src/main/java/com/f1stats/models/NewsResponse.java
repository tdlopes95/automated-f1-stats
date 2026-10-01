package com.f1stats.models;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** GET /news: headlines from public F1 feeds, newest first. */
public class NewsResponse {
    @SerializedName("items")          public List<Item> items = new ArrayList<>();
    /** Feeds that failed this time; the items come from the rest. */
    @SerializedName("failed_sources") public List<String> failedSources = new ArrayList<>();

    public static class Item {
        @SerializedName("title")         public String title;
        @SerializedName("link")          public String link;
        @SerializedName("source")        public String source;
        /** ISO-8601 UTC, or null when the feed has no dates. */
        @SerializedName("published_utc") public String publishedUtc;
    }
}
