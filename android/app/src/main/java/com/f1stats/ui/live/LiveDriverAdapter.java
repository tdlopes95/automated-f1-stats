package com.f1stats.ui.live;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;
import com.f1stats.models.LiveDriver;

import java.util.ArrayList;
import java.util.List;

public class LiveDriverAdapter extends RecyclerView.Adapter<LiveDriverAdapter.ViewHolder> {

    private List<LiveDriver> drivers = new ArrayList<>();

    public void setDrivers(List<LiveDriver> drivers) {
        this.drivers = drivers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_live_driver, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(drivers.get(position));
    }

    @Override
    public int getItemCount() { return drivers.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View teamColourStrip;
        TextView tvPosition, tvAcronym, tvTeam, tvGap, tvTyre, tvTyreAge, tvPits;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            teamColourStrip = itemView.findViewById(R.id.view_team_colour);
            tvPosition      = itemView.findViewById(R.id.tv_position);
            tvAcronym       = itemView.findViewById(R.id.tv_driver_acronym);
            tvTeam          = itemView.findViewById(R.id.tv_team_name);
            tvGap           = itemView.findViewById(R.id.tv_gap);
            tvTyre          = itemView.findViewById(R.id.tv_tyre);
            tvTyreAge       = itemView.findViewById(R.id.tv_tyre_age);
            tvPits          = itemView.findViewById(R.id.tv_pits);
        }

        void bind(LiveDriver driver) {
            tvPosition.setText(String.valueOf(driver.getPosition()));
            tvAcronym.setText(driver.getNameAcronym());
            tvTeam.setText(driver.getTeamName());
            tvGap.setText(driver.getDisplayGap());
            tvTyre.setText(driver.getCompoundShort());
            tvTyreAge.setText(driver.getTyreAge() != null ?
                    String.valueOf(driver.getTyreAge()) : "--");
            tvPits.setText(String.valueOf(driver.getPitStops()));

            // Team colour strip
            try {
                teamColourStrip.setBackgroundColor(
                        Color.parseColor(driver.getTeamColourHex()));
            } catch (Exception e) {
                teamColourStrip.setBackgroundColor(Color.WHITE);
            }

            // Tyre badge colour
            try {
                tvTyre.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                Color.parseColor(driver.getCompoundColour())));
                // Dark text on light tyres (Medium/Hard)
                String compound = driver.getCurrentCompound();
                if ("MEDIUM".equals(compound) || "HARD".equals(compound)) {
                    tvTyre.setTextColor(Color.BLACK);
                } else {
                    tvTyre.setTextColor(Color.WHITE);
                }
            } catch (Exception e) {
                tvTyre.setBackgroundTintList(null);
            }
        }
    }
}