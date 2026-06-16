package com.f1stats.ui.driver;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
        public final boolean hasFastestLap;

        public DriverRaceResult(int round, String raceName, String sessionType,
                                String position, String points, boolean isDnf,
                                boolean hasFastestLap) {
            this.round = round;
            this.raceName = raceName;
            this.sessionType = sessionType;
            this.position = position;
            this.points = points;
            this.isDnf = isDnf;
            this.hasFastestLap = hasFastestLap;
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
        View vFastestLap;
        final float cornerRadiusPx;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRound      = itemView.findViewById(R.id.tv_round);
            tvRaceName   = itemView.findViewById(R.id.tv_race_name);
            tvSession    = itemView.findViewById(R.id.tv_session);
            tvPosition   = itemView.findViewById(R.id.tv_position);
            tvPoints     = itemView.findViewById(R.id.tv_points);
            vFastestLap  = itemView.findViewById(R.id.v_fastest_lap);
            cornerRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4,
                    itemView.getContext().getResources().getDisplayMetrics());
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

            vFastestLap.setVisibility(result.hasFastestLap ? View.VISIBLE : View.GONE);

            GradientDrawable badge = new GradientDrawable();
            badge.setCornerRadius(cornerRadiusPx);

            if (result.isDnf) {
                badge.setColor(0xFFFF4444);
                tvPosition.setText("DNF");
                tvPosition.setTextColor(Color.WHITE);
            } else {
                int pos = parsePosition(result.position);
                tvPosition.setText("P" + result.position);
                if (pos == 1) {
                    badge.setColor(0xFFFFD700);
                    tvPosition.setTextColor(Color.BLACK);
                } else if (pos == 2) {
                    badge.setColor(0xFFC0C0C0);
                    tvPosition.setTextColor(Color.BLACK);
                } else if (pos == 3) {
                    badge.setColor(0xFFCD7F32);
                    tvPosition.setTextColor(Color.WHITE);
                } else {
                    badge.setColor(ContextCompat.getColor(itemView.getContext(), R.color.bg_surface));
                    tvPosition.setTextColor(Color.WHITE);
                }
            }
            tvPosition.setBackground(badge);

            try {
                double pts = Double.parseDouble(result.points);
                if (pts == (int) pts) {
                    tvPoints.setText("+" + (int) pts);
                } else {
                    tvPoints.setText("+" + pts);
                }
            } catch (NumberFormatException e) {
                tvPoints.setText("0");
            }
        }

        private int parsePosition(String pos) {
            try { return Integer.parseInt(pos); } catch (Exception e) { return 99; }
        }
    }
}
