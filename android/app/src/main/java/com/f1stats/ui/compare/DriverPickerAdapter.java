package com.f1stats.ui.compare;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.f1stats.R;
import com.f1stats.db.CachedDriver;

import java.util.ArrayList;
import java.util.List;

public class DriverPickerAdapter extends RecyclerView.Adapter<DriverPickerAdapter.ViewHolder> {

    interface OnDriverClickListener {
        void onDriverClick(CachedDriver driver);
    }

    private List<CachedDriver> drivers = new ArrayList<>();
    private final OnDriverClickListener listener;

    public DriverPickerAdapter(OnDriverClickListener listener) {
        this.listener = listener;
    }

    public void setDrivers(List<CachedDriver> drivers) {
        this.drivers = drivers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_driver_picker, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(drivers.get(position), listener);
    }

    @Override
    public int getItemCount() { return drivers.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivHeadshot;
        final TextView tvDriverName;
        final TextView tvTeamName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivHeadshot   = itemView.findViewById(R.id.iv_headshot);
            tvDriverName = itemView.findViewById(R.id.tv_driver_name);
            tvTeamName   = itemView.findViewById(R.id.tv_team_name);
        }

        void bind(CachedDriver driver, OnDriverClickListener listener) {
            String first = driver.firstName != null ? driver.firstName : "";
            String last  = driver.lastName  != null ? driver.lastName  : "";
            tvDriverName.setText((first + " " + last).trim());

            tvTeamName.setText(driver.teamName != null ? driver.teamName : "");
            try {
                tvTeamName.setTextColor(driver.teamColour != null
                        ? Color.parseColor(driver.teamColour) : Color.WHITE);
            } catch (Exception e) {
                tvTeamName.setTextColor(Color.WHITE);
            }

            if (driver.headshotUrl != null && !driver.headshotUrl.isEmpty()) {
                Glide.with(ivHeadshot)
                        .load(driver.headshotUrl)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.bg_headshot_placeholder)
                        .into(ivHeadshot);
            } else {
                Glide.with(ivHeadshot).clear(ivHeadshot);
                ivHeadshot.setImageDrawable(null);
                ivHeadshot.setBackgroundResource(R.drawable.bg_headshot_placeholder);
            }

            itemView.setOnClickListener(v -> listener.onDriverClick(driver));
        }
    }
}
