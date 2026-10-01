package com.f1stats.ui.results;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;
import com.f1stats.models.QualifyingResult;
import com.f1stats.models.RaceResult;
import com.f1stats.util.TeamColors;

import java.util.ArrayList;
import java.util.List;

public class QualifyingAdapter extends RecyclerView.Adapter<QualifyingAdapter.ViewHolder> {

    public enum QualiSession { Q1, Q2, Q3, ALL }

    private List<QualifyingResult> results = new ArrayList<>();
    private QualiSession session = QualiSession.ALL;

    public interface OnDriverClickListener {
        void onDriverClick(QualifyingResult result);
    }

    private OnDriverClickListener driverClickListener;

    public void setOnDriverClickListener(OnDriverClickListener listener) {
        this.driverClickListener = listener;
    }

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
        QualifyingResult result = results.get(position);
        holder.bind(result, session);
        holder.tvDriverName.setOnClickListener(v -> {
            if (driverClickListener != null) driverClickListener.onDriverClick(result);
        });
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
                            ContextCompat.getColor(itemView.getContext(), R.color.color_eliminated) :
                            ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
                    tvQ2Time.setVisibility(View.VISIBLE);
                    tvQ3Time.setVisibility(View.GONE);
                    break;
                case Q3:
                    tvQ1Time.setVisibility(View.GONE);
                    tvQ2Time.setVisibility(View.GONE);
                    tvQ3Time.setText(result.getQ3().equals("--") ?
                            "eliminated Q2" : result.getQ3());
                    tvQ3Time.setTextColor(result.getQ3().equals("--") ?
                            ContextCompat.getColor(itemView.getContext(), R.color.color_eliminated) :
                            Color.WHITE);
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
            RaceResult.Constructor team = result.getConstructor();
            teamColourStrip.setBackgroundColor(TeamColors.get(itemView.getContext(),
                    team != null ? team.getConstructorId() : null,
                    team != null ? team.getName() : null, null));
        }

    }
}