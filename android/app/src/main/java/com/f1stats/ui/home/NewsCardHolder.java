package com.f1stats.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.R;
import com.f1stats.home.HomeCardType;
import com.f1stats.models.NewsResponse;
import com.f1stats.ui.news.NewsRow;

import java.util.Map;

/** NEWS: the newest headlines from the chosen sources, plus a link to the full list. */
class NewsCardHolder extends HomeCardHolder<HomeCardState.News> {

    private final LinearLayout llItems;
    private final TextView tvEmpty;

    NewsCardHolder(@NonNull View itemView) {
        super(itemView, HomeCardType.NEWS);
        llItems = itemView.findViewById(R.id.ll_news_items);
        tvEmpty = itemView.findViewById(R.id.tv_news_empty);
        itemView.findViewById(R.id.btn_news_more).setOnClickListener(v -> {
            if (callbacks != null) callbacks.onOpenNews();
        });
    }

    @Override
    void bind(@NonNull HomeCardState.News state, @Nullable Map<String, String> headshots) {
        llItems.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
        long now = System.currentTimeMillis();
        for (NewsResponse.Item item : state.items) {
            View row = inflater.inflate(R.layout.item_news_headline, llItems, false);
            NewsRow.bind(row, item, now);
            row.setOnClickListener(v -> {
                if (callbacks != null && item.link != null) callbacks.onOpenLink(item.link);
            });
            llItems.addView(row);
        }
        if (state.items.isEmpty()) {
            tvEmpty.setText(state.noneFromSources ? R.string.news_no_matching : R.string.news_empty);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }
}
