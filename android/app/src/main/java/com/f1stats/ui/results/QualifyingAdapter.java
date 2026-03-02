package com.f1stats.ui.results;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;
import com.f1stats.models.QualifyingResult;

import java.util.ArrayList;
import java.util.List;

public class QualifyingAdapter extends RecyclerView.Adapter<QualifyingAdapter.ViewHolder> {

    public enum QualiSession { Q1, Q2, Q3, ALL }

    private List<QualifyingResult> results = new ArrayList<>();
    private QualiSession session = QualiSession.ALL;

    public void setResults(List<QualifyingResult> results, QualiSession session) {
        this.results = results;
        this.session = session;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_qualifying_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(results.get(position), session);
    }

    @Override
    public int getItemCount() { return results.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View teamColourStrip;
        TextView tvPosition, tvDriverName, tvTeamName;
        TextView tvQ1Time, tvQ2Time, tvQ3Time;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            teamColourStrip = itemView.findViewById(R.id.view_team_colour);
            tvPosition      = itemView.findViewById(R.id.tv_position);
            tvDriverName    = itemView.findViewById(R.id.tv_driver_name);
            tvTeamName      = itemView.findViewById(R.id.tv_team_name);
            tvQ1Time        = itemView.findViewById(R.id.tv_q1_time);
            tvQ2Time        = itemView.findViewById(R.id.tv_q2_time);
            tvQ3Time        = itemView.findViewById(R.id.tv_q3_time);
        }

        void bind(QualifyingResult result, QualiSession session) {
            tvPosition.setText(result.getPosition());

            if (result.getDriver() != null) {
                tvDriverName.setText(result.getDriver().getFullName());
            }
            if (result.getConstructor() != null) {
                tvTeamName.setText(result.getConstructor().getName());
            }

            // Show times based on selected session
            switch (session) {
                case Q1:
                    tvQ1Time.setText(result.getQ1());
                    tvQ1Time.setVisibility(View.VISIBLE);
                    tvQ2Time.setVisibility(View.GONE);
                    tvQ3Time.setVisibility(View.GONE);
                    break;
                case Q2:
                    tvQ1Time.setVisibility(View.GONE);
                    tvQ2Time.setText(result.getQ2().equals("--") ?
                            "eliminated Q1" : result.getQ2());
                    tvQ2Time.setTextColor(result.getQ2().equals("--") ?
                            Color.parseColor("#FF4444") : Color.parseColor("#AAAAAA"));
                    tvQ2Time.setVisibility(View.VISIBLE);
                    tvQ3Time.setVisibility(View.GONE);
                    break;
                case Q3:
                    tvQ1Time.setVisibility(View.GONE);
                    tvQ2Time.setVisibility(View.GONE);
                    tvQ3Time.setText(result.getQ3().equals("--") ?
                            "eliminated Q2" : result.getQ3());
                    tvQ3Time.setTextColor(result.getQ3().equals("--") ?
                            Color.parseColor("#FF4444") : Color.WHITE);
                    tvQ3Time.setVisibility(View.VISIBLE);
                    break;
                default: // ALL
                    tvQ1Time.setText("Q1: " + result.getQ1());
                    tvQ2Time.setText("Q2: " + result.getQ2());
                    tvQ3Time.setText("Q3: " + result.getQ3());
                    tvQ1Time.setVisibility(View.VISIBLE);
                    tvQ2Time.setVisibility(View.VISIBLE);
                    tvQ3Time.setVisibility(View.VISIBLE);
                    break;
            }

            // Team colour strip
            setTeamColour(result.getConstructor() != null ?
                    result.getConstructor().getName() : "");
        }

        private void setTeamColour(String teamName) {
            java.util.Map<String, String> colours = new java.util.HashMap<>();
            colours.put("Red Bull",      "#3671C6");
            colours.put("Ferrari",       "#E8002D");
            colours.put("Mercedes",      "#27F4D2");
            colours.put("McLaren",       "#FF8000");
            colours.put("Aston Martin",  "#229971");
            colours.put("Alpine",        "#FF87BC");
            colours.put("Williams",      "#64C4FF");
            colours.put("RB",            "#6692FF");
            colours.put("Haas",          "#B6BABD");
            colours.put("Audi",          "#B5B5B5");
            colours.put("Kick Sauber",   "#52E252");
            colours.put("Sauber",        "#52E252");
            colours.put("Cadillac",      "#CC0000");

            String colour = "#FFFFFF";
            for (java.util.Map.Entry<String, String> entry : colours.entrySet()) {
                if (teamName.contains(entry.getKey())) {
                    colour = entry.getValue();
                    break;
                }
            }
            try {
                teamColourStrip.setBackgroundColor(Color.parseColor(colour));
            } catch (Exception e) {
                teamColourStrip.setBackgroundColor(Color.WHITE);
            }
        }
    }
}