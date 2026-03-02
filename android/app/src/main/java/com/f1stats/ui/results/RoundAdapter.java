package com.f1stats.ui.results;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;
import com.f1stats.models.RoundSchedule;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RoundAdapter extends RecyclerView.Adapter<RoundAdapter.ViewHolder> {

    public interface OnRoundClickListener {
        void onRoundClick(RoundSchedule round);
    }

    private List<RoundSchedule> rounds = new ArrayList<>();
    private OnRoundClickListener listener;

    public void setRounds(List<RoundSchedule> rounds) {
        this.rounds = rounds;
        notifyDataSetChanged();
    }

    public void setOnRoundClickListener(OnRoundClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_round, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RoundSchedule round = rounds.get(position);
        holder.bind(round);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRoundClick(round);
        });
    }

    @Override
    public int getItemCount() { return rounds.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRoundNumber, tvRaceName, tvCircuit, tvRaceDate;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRoundNumber = itemView.findViewById(R.id.tv_round_number);
            tvRaceName    = itemView.findViewById(R.id.tv_race_name);
            tvCircuit     = itemView.findViewById(R.id.tv_circuit);
            tvRaceDate    = itemView.findViewById(R.id.tv_race_date);
        }

        void bind(RoundSchedule round) {
            tvRoundNumber.setText("R" + round.getRound());
            tvRaceName.setText(round.getRaceName());
            tvCircuit.setText(round.getCircuit());
            tvRaceDate.setText(formatDate(round.getRaceDate()));
        }

        private String formatDate(String isoStr) {
            if (isoStr == null) return "--";
            try {
                SimpleDateFormat input  = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat output = new SimpleDateFormat(
                        "dd MMM", Locale.getDefault());
                Date date = input.parse(isoStr);
                return date != null ? output.format(date) : isoStr;
            } catch (Exception e) {
                return isoStr;
            }
        }
    }
}