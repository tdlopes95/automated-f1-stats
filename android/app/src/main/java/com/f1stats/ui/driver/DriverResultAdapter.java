package com.f1stats.ui.driver;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;

import java.util.ArrayList;
import java.util.List;

public class DriverResultAdapter extends RecyclerView.Adapter<DriverResultAdapter.ViewHolder> {

    public static class DriverRaceResult {
        public final int round;
        public final String raceName;
        public final String sessionType; // "Race" or "Sprint"
        public final String position;
        public final String points;
        public final boolean isDnf;

        public DriverRaceResult(int round, String raceName, String sessionType,
                                String position, String points, boolean isDnf) {
            this.round = round;
            this.raceName = raceName;
            this.sessionType = sessionType;
            this.position = position;
            this.points = points;
            this.isDnf = isDnf;
        }
    }

    private List<DriverRaceResult> results = new ArrayList<>();

    public void setResults(List<DriverRaceResult> results) {
        this.results = results;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_driver_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(results.get(position));
    }

    @Override
    public int getItemCount() { return results.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRound, tvRaceName, tvSession, tvPosition, tvPoints;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRound    = itemView.findViewById(R.id.tv_round);
            tvRaceName = itemView.findViewById(R.id.tv_race_name);
            tvSession  = itemView.findViewById(R.id.tv_session);
            tvPosition = itemView.findViewById(R.id.tv_position);
            tvPoints   = itemView.findViewById(R.id.tv_points);
        }

        void bind(DriverRaceResult result) {
            tvRound.setText("R" + result.round);
            tvRaceName.setText(result.raceName);

            if ("Sprint".equals(result.sessionType)) {
                tvSession.setText("SPR");
                tvSession.setVisibility(View.VISIBLE);
            } else {
                tvSession.setVisibility(View.GONE);
            }

            if (result.isDnf) {
                tvPosition.setText("DNF");
                tvPosition.setTextColor(Color.parseColor("#FF4444"));
            } else {
                int pos = parsePosition(result.position);
                tvPosition.setText("P" + result.position);
                if (pos == 1) {
                    tvPosition.setTextColor(Color.parseColor("#FFD700")); // gold
                } else if (pos == 2) {
                    tvPosition.setTextColor(Color.parseColor("#C0C0C0")); // silver
                } else if (pos == 3) {
                    tvPosition.setTextColor(Color.parseColor("#CD7F32")); // bronze
                } else {
                    tvPosition.setTextColor(Color.WHITE);
                }
            }

            try {
                double pts = Double.parseDouble(result.points);
                tvPoints.setText("+" + (int) pts + " pts");
            } catch (NumberFormatException e) {
                tvPoints.setText("0 pts");
            }
        }

        private int parsePosition(String pos) {
            try { return Integer.parseInt(pos); } catch (Exception e) { return 99; }
        }
    }
}
