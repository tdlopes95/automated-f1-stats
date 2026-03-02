package com.f1stats.ui.standings;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.DriverHelper;
import com.f1stats.R;
import com.f1stats.models.ConstructorStanding;
import com.f1stats.models.DriverStanding;
import com.f1stats.models.PitStop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StandingsAdapter extends RecyclerView.Adapter<StandingsAdapter.ViewHolder> {

    private static final int MODE_DRIVERS      = 0;
    private static final int MODE_CONSTRUCTORS = 1;
    private static final int MODE_PIT_STOPS    = 2;

    private int mode = MODE_DRIVERS;
    private List<DriverStanding> driverStandings = new ArrayList<>();
    private List<ConstructorStanding> constructorStandings = new ArrayList<>();
    private List<PitStop> pitStops = new ArrayList<>();

    // DNF map: driverId → dnf count (populated separately)
    private Map<String, Integer> dnfMap = new java.util.HashMap<>();
    // Podium map: driverId → podium count
    private Map<String, Integer> podiumMap = new java.util.HashMap<>();

    public void setDriverStandings(List<DriverStanding> data) {
        this.driverStandings = data;
        this.mode = MODE_DRIVERS;
        notifyDataSetChanged();
    }

    public void setConstructorStandings(List<ConstructorStanding> data) {
        this.constructorStandings = data;
        this.mode = MODE_CONSTRUCTORS;
        notifyDataSetChanged();
    }

    public void setPitStopMode(List<PitStop> data) {
        this.pitStops = data;
        this.mode = MODE_PIT_STOPS;
        notifyDataSetChanged();
    }

    public void setDnfMap(Map<String, Integer> dnfMap) {
        this.dnfMap = dnfMap;
        notifyDataSetChanged();
    }

    public void setPodiumMap(Map<String, Integer> podiumMap) {
        this.podiumMap = podiumMap;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_standing, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        switch (mode) {
            case MODE_DRIVERS:
                holder.bindDriver(driverStandings.get(position), dnfMap, podiumMap);
                break;
            case MODE_CONSTRUCTORS:
                holder.bindConstructor(constructorStandings.get(position));
                break;
            case MODE_PIT_STOPS:
                holder.bindPitStop(pitStops.get(position), position + 1);
                break;
        }
    }

    @Override
    public int getItemCount() {
        switch (mode) {
            case MODE_DRIVERS:      return driverStandings.size();
            case MODE_CONSTRUCTORS: return constructorStandings.size();
            case MODE_PIT_STOPS:    return pitStops.size();
            default:                return 0;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View teamColourStrip;
        TextView tvPosition, tvFlag, tvName, tvNumber;
        TextView tvSubtitle, tvDnf, tvPoints, tvWins, tvPodiums;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            teamColourStrip = itemView.findViewById(R.id.view_team_colour);
            tvPosition      = itemView.findViewById(R.id.tv_position);
            tvFlag          = itemView.findViewById(R.id.tv_flag);
            tvName          = itemView.findViewById(R.id.tv_name);
            tvNumber        = itemView.findViewById(R.id.tv_number);
            tvSubtitle      = itemView.findViewById(R.id.tv_subtitle);
            tvDnf           = itemView.findViewById(R.id.tv_dnf);
            tvPoints        = itemView.findViewById(R.id.tv_points);
            tvWins          = itemView.findViewById(R.id.tv_wins);
            tvPodiums       = itemView.findViewById(R.id.tv_podiums);
        }

        void bindDriver(DriverStanding standing,
                        Map<String, Integer> dnfMap,
                        Map<String, Integer> podiumMap) {
            tvPosition.setText(standing.getPosition());

            if (standing.getDriver() != null) {
                tvFlag.setText(DriverHelper.getFlag(
                        standing.getDriver().getNationality()));
                tvName.setText(standing.getDriver().getFullName());
                tvNumber.setText(DriverHelper.getNumberDisplay(
                        standing.getDriver().getNumber()));

                // DNF count
                String driverId = standing.getDriver().getDriverId();
                Integer dnfs = dnfMap.get(driverId);
                if (dnfs != null && dnfs > 0) {
                    tvDnf.setText("DNF ×" + dnfs);
                    tvDnf.setVisibility(View.VISIBLE);
                } else {
                    tvDnf.setVisibility(View.GONE);
                }

                // Podiums
                Integer podiums = podiumMap.get(driverId);
                if (podiums != null && podiums > 0) {
                    tvPodiums.setText("🏆 " + podiums);
                    tvPodiums.setVisibility(View.VISIBLE);
                } else {
                    tvPodiums.setVisibility(View.GONE);
                }
            }

            tvSubtitle.setText(standing.getTeamName());
            tvPoints.setText(standing.getPoints());
            tvWins.setText(standing.getWins() + " wins");
            setTeamColour(standing.getTeamName());
        }

        void bindConstructor(ConstructorStanding standing) {
            tvPosition.setText(standing.getPosition());
            tvFlag.setText(standing.getConstructor() != null ?
                    DriverHelper.getFlag(standing.getConstructor().getNationality()) : "");
            tvName.setText(standing.getConstructor() != null ?
                    standing.getConstructor().getName() : "Unknown");
            tvNumber.setText("");
            tvSubtitle.setText(standing.getConstructor() != null ?
                    standing.getConstructor().getNationality() : "");
            tvDnf.setVisibility(View.GONE);
            tvPoints.setText(standing.getPoints());
            tvWins.setText(standing.getWins() + " wins");
            tvPodiums.setVisibility(View.GONE);
            setTeamColour(standing.getConstructor() != null ?
                    standing.getConstructor().getName() : "");
        }

        void bindPitStop(PitStop stop, int rank) {
            tvPosition.setText(String.valueOf(rank));
            tvFlag.setText("🔧");
            tvName.setText(stop.getDriverName() != null ?
                    stop.getDriverName() : "Driver " + stop.getDriverNumber());
            tvNumber.setText("");
            tvSubtitle.setText(stop.getTeamName() != null ? stop.getTeamName() : "");
            tvDnf.setVisibility(View.GONE);
            tvPodiums.setVisibility(View.GONE);
            tvPoints.setText(stop.getFormattedStopDuration()); // pit time
            tvWins.setText("Lap " + stop.getLapNumber());
            try {
                teamColourStrip.setBackgroundColor(
                        Color.parseColor(stop.getTeamColourHex()));
            } catch (Exception e) {
                teamColourStrip.setBackgroundColor(Color.WHITE);
            }
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
            colours.put("RB F1 Team",    "#6692FF");
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