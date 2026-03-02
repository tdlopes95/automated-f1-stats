package com.f1stats.ui.results;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;
import com.f1stats.models.PitStop;

import java.util.ArrayList;
import java.util.List;

public class PitStopAdapter extends RecyclerView.Adapter<PitStopAdapter.ViewHolder> {

    private List<PitStop> pitStops = new ArrayList<>();

    public void setPitStops(List<PitStop> pitStops) {
        this.pitStops = pitStops;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pit_stop, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(pitStops.get(position), position + 1);
    }

    @Override
    public int getItemCount() { return pitStops.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View teamColourStrip;
        TextView tvRank, tvDriverName, tvTeamName, tvLap, tvDuration;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            teamColourStrip = itemView.findViewById(R.id.view_team_colour);
            tvRank          = itemView.findViewById(R.id.tv_rank);
            tvDriverName    = itemView.findViewById(R.id.tv_driver_name);
            tvTeamName      = itemView.findViewById(R.id.tv_team_name);
            tvLap           = itemView.findViewById(R.id.tv_lap);
            tvDuration      = itemView.findViewById(R.id.tv_duration);
        }

        void bind(PitStop stop, int rank) {
            tvRank.setText(String.valueOf(rank));
            tvDriverName.setText(stop.getDriverName() != null ?
                    stop.getDriverName() : "Driver " + stop.getDriverNumber());
            tvTeamName.setText(stop.getTeamName() != null ? stop.getTeamName() : "");
            tvLap.setText("Lap " + stop.getLapNumber());
            tvDuration.setText(stop.getFormattedStopDuration());

            try {
                teamColourStrip.setBackgroundColor(
                        Color.parseColor(stop.getTeamColourHex()));
            } catch (Exception e) {
                teamColourStrip.setBackgroundColor(Color.WHITE);
            }
        }
    }
}