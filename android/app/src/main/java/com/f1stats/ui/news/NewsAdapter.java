package com.f1stats.ui.news;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;
import com.f1stats.models.NewsResponse;

import java.util.ArrayList;
import java.util.List;

/** Headline rows for NewsActivity (same row layout as the NEWS Home card). */
class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.Holder> {

    interface OnItemClickListener {
        void onItemClick(@NonNull NewsResponse.Item item);
    }

    private final List<NewsResponse.Item> items = new ArrayList<>();
    private final OnItemClickListener listener;

    NewsAdapter(@NonNull OnItemClickListener listener) {
        this.listener = listener;
    }

    @SuppressWarnings("NotifyDataSetChanged")   // the whole list is replaced on refresh
    void setItems(@NonNull List<NewsResponse.Item> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news_headline, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        NewsResponse.Item item = items.get(position);
        NewsRow.bind(holder.itemView, item, System.currentTimeMillis());
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    static class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
