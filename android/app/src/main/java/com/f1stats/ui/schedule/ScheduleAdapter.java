package com.f1stats.ui.schedule;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.bumptech.glide.Glide;
import com.f1stats.DateHelper;
import com.f1stats.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private List<Map<String, Object>> schedule = new ArrayList<>();

    public void setSchedule(List<Map<String, Object>> schedule) {
        this.schedule = schedule;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_schedule_race, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(schedule.get(position));
    }

    @Override
    public int getItemCount() { return schedule.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRound, tvRaceName, tvCircuit, tvRaceDate, tvExpand;
        LinearLayout llSessions;
        ImageView ivCircuitImage, ivCountryFlag;
        boolean expanded = false;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRound        = itemView.findViewById(R.id.tv_round);
            tvRaceName     = itemView.findViewById(R.id.tv_race_name);
            tvCircuit      = itemView.findViewById(R.id.tv_circuit);
            tvRaceDate     = itemView.findViewById(R.id.tv_race_date);
            tvExpand       = itemView.findViewById(R.id.tv_expand);
            llSessions     = itemView.findViewById(R.id.ll_sessions);
            ivCircuitImage = itemView.findViewById(R.id.iv_circuit_image);
            ivCountryFlag  = itemView.findViewById(R.id.iv_country_flag);
        }

        @SuppressWarnings("unchecked")
        void bind(Map<String, Object> race) {
            Object roundObj = race.get("round");
            tvRound.setText("R" + (roundObj != null ? roundObj.toString() : "?"));
            tvRaceName.setText(getString(race, "race_name", "Unknown"));
            tvCircuit.setText(getString(race, "circuit", ""));

            // Find race date from sessions
            List<Map<String, Object>> sessions =
                    (List<Map<String, Object>>) race.get("sessions");
            if (sessions != null) {
                for (Map<String, Object> session : sessions) {
                    if ("Race".equals(session.get("name"))) {
                        tvRaceDate.setText(formatDate((String) session.get("datetime")));
                        break;
                    }
                }

                // Build session rows (hidden until expanded)
                llSessions.removeAllViews();
                for (Map<String, Object> session : sessions) {
                    TextView tv = new TextView(itemView.getContext());
                    String name = getString(session, "name", "");
                    String date = formatDate((String) session.get("datetime"));
                    tv.setText(name + "  ·  " + date);
                    tv.setTextColor(0xFFAAAAAA);
                    tv.setTextSize(12f);
                    tv.setPadding(0, 4, 0, 4);
                    llSessions.addView(tv);
                }
            }

            // Country flag
            String countryFlag = (String) race.get("country_flag");
            if (countryFlag != null && !countryFlag.isEmpty()) {
                ivCountryFlag.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext()).load(countryFlag).into(ivCountryFlag);
            } else {
                ivCountryFlag.setVisibility(View.GONE);
            }

            // Circuit image
            String circuitImage = (String) race.get("circuit_image");
            if (circuitImage != null && !circuitImage.isEmpty()) {
                Glide.with(itemView.getContext()).load(circuitImage).into(ivCircuitImage);
            } else {
                ivCircuitImage.setImageDrawable(null);
            }

            // Expand / collapse on click
            expanded = false;
            llSessions.setVisibility(View.GONE);
            ivCircuitImage.setVisibility(View.GONE);
            tvExpand.setText("▼ Sessions");

            itemView.setOnClickListener(v -> {
                expanded = !expanded;
                TransitionManager.beginDelayedTransition(
                        (ViewGroup) itemView, new AutoTransition());
                llSessions.setVisibility(expanded ? View.VISIBLE : View.GONE);
                ivCircuitImage.setVisibility(
                        (expanded && circuitImage != null && !circuitImage.isEmpty())
                                ? View.VISIBLE : View.GONE);
                tvExpand.setText(expanded ? "▲ Sessions" : "▼ Sessions");
            });
        }

        private String formatDate(String isoStr) {
            return DateHelper.formatShort(isoStr);
        }

        private String getString(Map<String, Object> map, String key, String fallback) {
            Object val = map.get(key);
            return val != null ? val.toString() : fallback;
        }
    }
}