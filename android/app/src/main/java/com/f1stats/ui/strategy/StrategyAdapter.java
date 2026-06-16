package com.f1stats.ui.strategy;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StrategyAdapter extends RecyclerView.Adapter<StrategyAdapter.DriverHolder> {

    private List<Map<String, Object>> drivers = new ArrayList<>();
    private int totalLaps = 1;
    private Map<String, Integer> finishPositions = new HashMap<>();

    public void setData(List<Map<String, Object>> drivers, int totalLaps,
                        Map<String, Integer> finishPositions) {
        this.drivers        = drivers != null ? drivers : new ArrayList<>();
        this.totalLaps      = totalLaps;
        this.finishPositions = finishPositions != null ? finishPositions : new HashMap<>();
        notifyDataSetChanged();
    }

    @Override public int getItemCount() { return drivers.size(); }

    @NonNull
    @Override
    public DriverHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new DriverHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tyre_strategy, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull DriverHolder holder, int position) {
        holder.bind(drivers.get(position), totalLaps, finishPositions, position);
    }

    static class DriverHolder extends RecyclerView.ViewHolder {
        final TextView tvFinishPos;
        final TextView tvCode;
        final TextView tvNumber;
        final TyreStrategyView strategyView;

        DriverHolder(@NonNull View v) {
            super(v);
            tvFinishPos  = v.findViewById(R.id.tv_finish_pos);
            tvCode       = v.findViewById(R.id.tv_driver_code);
            tvNumber     = v.findViewById(R.id.tv_driver_number);
            strategyView = v.findViewById(R.id.tyre_strategy_view);
        }

        void bind(Map<String, Object> driver, int totalLaps,
                  Map<String, Integer> finishPositions, int index) {
            // alternating row background
            itemView.setBackgroundColor(index % 2 == 0
                    ? itemView.getContext().getColor(R.color.bg_surface)
                    : itemView.getContext().getColor(R.color.bg_dark));

            String code = strVal(driver.get("code"), "???");
            tvCode.setText(code);

            // driver code in team colour
            String teamColour = strVal(driver.get("team_colour"), null);
            if (teamColour != null) {
                try {
                    tvCode.setTextColor(Color.parseColor(teamColour));
                } catch (Exception e) {
                    tvCode.setTextColor(Color.WHITE);
                }
            } else {
                tvCode.setTextColor(Color.WHITE);
            }

            // finishing position
            Integer finPos = finishPositions.get(code);
            if (tvFinishPos != null) {
                tvFinishPos.setText(finPos != null ? "P" + finPos : "");
            }

            // driver number
            Object num = driver.get("driver_number");
            tvNumber.setText(num != null ? "#" + intVal(num) : "");

            // stints
            List<Map<String, Object>> stints = new ArrayList<>();
            Object raw = driver.get("stints");
            if (raw instanceof List) {
                for (Object s : (List<?>) raw) {
                    if (s instanceof Map) {
                        //noinspection unchecked
                        stints.add((Map<String, Object>) s);
                    }
                }
            }
            strategyView.setStints(stints, totalLaps);
        }
    }

    private static String strVal(Object v, String fallback) {
        return v != null && !v.toString().isEmpty() ? v.toString() : fallback;
    }

    private static int intVal(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }
}
