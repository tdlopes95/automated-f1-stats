package com.f1stats.ui.news;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.f1stats.R;
import com.f1stats.models.NewsResponse;
import com.f1stats.util.RelativeTime;

/** Binds item_news_headline, shared by the NEWS Home card and NewsActivity. */
public final class NewsRow {

    private NewsRow() {}

    public static void bind(@NonNull View row, @NonNull NewsResponse.Item item, long nowMs) {
        Context context = row.getContext();
        ((TextView) row.findViewById(R.id.tv_news_title)).setText(item.title);
        String source = item.source != null ? item.source : "";
        String age = RelativeTime.format(context, item.publishedUtc, nowMs);
        ((TextView) row.findViewById(R.id.tv_news_meta)).setText(age != null
                ? context.getString(R.string.news_meta, source, age) : source);
    }
}
