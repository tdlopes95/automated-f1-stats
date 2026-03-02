package com.f1stats.ui.results;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;
import com.f1stats.models.RaceResult;

import java.util.ArrayList;
import java.util.List;

public class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.ViewHolder> {

    private List<RaceResult> results = new ArrayList<>();

    private static java.util.Map<String, String> TEAM_COLOURS = new java.util.HashMap<>();
    static {
        TEAM_COLOURS.put("Red Bull",      "#3671C6");
        TEAM_COLOURS.put("Ferrari",       "#E8002D");
        TEAM_COLOURS.put("Mercedes",      "#27F4D2");
        TEAM_COLOURS.put("McLaren",       "#FF8000");
        TEAM_COLOURS.put("Aston Martin",  "#229971");
        TEAM_COLOURS.put("Alpine",        "#FF87BC");
        TEAM_COLOURS.put("Williams",      "#64C4FF");
        TEAM_COLOURS.put("RB",            "#6692FF");
        TEAM_COLOURS.put("Haas",          "#B6BABD");
        TEAM_COLOURS.put("Audi",          "#B5B5B5");
        TEAM_COLOURS.put("Kick Sauber",   "#52E252");
        TEAM_COLOURS.put("Sauber",        "#52E252");
        TEAM_COLOURS.put("Cadillac",      "#CC0000");
    }

    public void setResults(List<RaceResult> results) {
        this.results = results;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(results.get(position));
    }

    @Override
    public int getItemCount() { return results.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View teamColourStrip;
        TextView tvPosition, tvDriverName, tvTeamName;
        TextView tvTime, tvFastestLapTime, tvPoints, tvFastestLap;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            teamColourStrip  = itemView.findViewById(R.id.view_team_colour);
            tvPosition       = itemView.findViewById(R.id.tv_position);
            tvDriverName     = itemView.findViewById(R.id.tv_driver_name);
            tvTeamName       = itemView.findViewById(R.id.tv_team_name);
            tvTime           = itemView.findViewById(R.id.tv_time);
            tvFastestLapTime = itemView.findViewById(R.id.tv_fastest_lap_time);
            tvPoints         = itemView.findViewById(R.id.tv_points);
            tvFastestLap     = itemView.findViewById(R.id.tv_fastest_lap);
        }

        void bind(RaceResult result) {
            tvPosition.setText(result.getPosition());

            if (result.getDriver() != null) {
                tvDriverName.setText(result.getDriver().getFullName());
            }
            if (result.getConstructor() != null) {
                tvTeamName.setText(result.getConstructor().getName());
            }

            tvTime.setText(result.getDisplayTime());
            tvPoints.setText(result.getPoints() + " pts");

            // Fastest lap indicator emoji
            tvFastestLap.setVisibility(
                    result.hasFastestLap() ? View.VISIBLE : View.GONE);

            // Fastest lap time in purple
            if (result.getFastestLap() != null &&
                    result.getFastestLap().getTime() != null) {
                String lapTime = result.getFastestLap().getTime().getTime();
                tvFastestLapTime.setText(lapTime != null ? "⚡ " + lapTime : "");
                tvFastestLapTime.setVisibility(
                        result.hasFastestLap() ? View.VISIBLE : View.GONE);
            } else {
                tvFastestLapTime.setVisibility(View.GONE);
            }

            // Team colour strip
            String teamName = result.getConstructor() != null ?
                    result.getConstructor().getName() : "";
            try {
                teamColourStrip.setBackgroundColor(
                        Color.parseColor(getTeamColour(teamName)));
            } catch (Exception e) {
                teamColourStrip.setBackgroundColor(Color.WHITE);
            }
        }

        private String getTeamColour(String teamName) {
            for (java.util.Map.Entry<String, String> entry : TEAM_COLOURS.entrySet()) {
                if (teamName.contains(entry.getKey())) return entry.getValue();
            }
            return "#FFFFFF";
        }
    }
}