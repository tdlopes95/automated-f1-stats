package com.f1stats.ui.strategy;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StrategyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_LEGEND = 0;
    private static final int TYPE_DRIVER = 1;

    private List<Map<String, Object>> drivers = new ArrayList<>();
    private int totalLaps = 1;

    public void setData(List<Map<String, Object>> drivers, int totalLaps) {
        this.drivers   = drivers != null ? drivers : new ArrayList<>();
        this.totalLaps = totalLaps;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_LEGEND : TYPE_DRIVER;
    }

    @Override
    public int getItemCount() {
        return drivers.isEmpty() ? 0 : drivers.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_LEGEND) {
            return new LegendHolder(inf.inflate(R.layout.item_strategy_legend, parent, false));
        }
        return new DriverHolder(inf.inflate(R.layout.item_tyre_strategy, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DriverHolder) {
            ((DriverHolder) holder).bind(drivers.get(position - 1), totalLaps);
        }
    }

    // ── Legend ────────────────────────────────────────────────────────────────

    static class LegendHolder extends RecyclerView.ViewHolder {
        LegendHolder(@NonNull View itemView) {
            super(itemView);
            setDot(itemView, R.id.dot_soft,   "#FF3333");
            setDot(itemView, R.id.dot_medium, "#FFD700");
            setDot(itemView, R.id.dot_hard,   "#FFFFFF");
            setDot(itemView, R.id.dot_inter,  "#39B54A");
            setDot(itemView, R.id.dot_wet,    "#3399FF");
        }

        private static void setDot(View root, int id, String hex) {
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.parseColor(hex));
            root.findViewById(id).setBackground(circle);
        }
    }

    // ── Driver row ────────────────────────────────────────────────────────────

    static class DriverHolder extends RecyclerView.ViewHolder {
        final TextView tvCode;
        final TextView tvNumber;
        final TyreStrategyView strategyView;

        DriverHolder(@NonNull View v) {
            super(v);
            tvCode       = v.findViewById(R.id.tv_driver_code);
            tvNumber     = v.findViewById(R.id.tv_driver_number);
            strategyView = v.findViewById(R.id.tyre_strategy_view);
        }

        void bind(Map<String, Object> driver, int totalLaps) {
            tvCode.setText(strVal(driver.get("code"), "???"));
            Object num = driver.get("driver_number");
            tvNumber.setText(num != null ? "#" + intVal(num) : "");

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
