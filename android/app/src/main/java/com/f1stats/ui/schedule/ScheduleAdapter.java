package com.f1stats.ui.schedule;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
        boolean expanded = false;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRound    = itemView.findViewById(R.id.tv_round);
            tvRaceName = itemView.findViewById(R.id.tv_race_name);
            tvCircuit  = itemView.findViewById(R.id.tv_circuit);
            tvRaceDate = itemView.findViewById(R.id.tv_race_date);
            tvExpand   = itemView.findViewById(R.id.tv_expand);
            llSessions = itemView.findViewById(R.id.ll_sessions);
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

            // Expand / collapse on click
            expanded = false;
            llSessions.setVisibility(View.GONE);
            tvExpand.setText("▼ Sessions");

            itemView.setOnClickListener(v -> {
                expanded = !expanded;
                llSessions.setVisibility(expanded ? View.VISIBLE : View.GONE);
                tvExpand.setText(expanded ? "▲ Sessions" : "▼ Sessions");
            });
        }

        private String formatDate(String isoStr) {
            if (isoStr == null) return "--";
            try {
                SimpleDateFormat input  = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat output = new SimpleDateFormat(
                        "EEE dd MMM, HH:mm", Locale.getDefault());
                Date date = input.parse(isoStr);
                return date != null ? output.format(date) : isoStr;
            } catch (Exception e) {
                return isoStr;
            }
        }

        private String getString(Map<String, Object> map, String key, String fallback) {
            Object val = map.get(key);
            return val != null ? val.toString() : fallback;
        }
    }
}