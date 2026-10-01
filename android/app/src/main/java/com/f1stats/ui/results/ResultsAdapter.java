package com.f1stats.ui.results;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;
import com.f1stats.models.RaceResult;
import com.f1stats.util.TeamColors;

import java.util.ArrayList;
import java.util.List;

public class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.ViewHolder> {

    private List<RaceResult> results = new ArrayList<>();


    public interface OnDriverClickListener {
        void onDriverClick(RaceResult result);
    }

    private OnDriverClickListener driverClickListener;

    public void setOnDriverClickListener(OnDriverClickListener listener) {
        this.driverClickListener = listener;
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
        RaceResult result = results.get(position);
        holder.bind(result);
        holder.tvDriverName.setOnClickListener(v -> {
            if (driverClickListener != null) driverClickListener.onDriverClick(result);
        });
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
            RaceResult.Constructor team = result.getConstructor();
            teamColourStrip.setBackgroundColor(TeamColors.get(itemView.getContext(),
                    team != null ? team.getConstructorId() : null,
                    team != null ? team.getName() : null, null));
        }
    }
}