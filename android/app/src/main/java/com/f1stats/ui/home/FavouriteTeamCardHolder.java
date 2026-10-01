package com.f1stats.ui.home;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.R;
import com.f1stats.home.HomeCardType;
import com.f1stats.util.TeamColors;

import java.util.Map;

/** FAVOURITE_TEAM: constructor standing, both drivers' points and their race H2H. */
class FavouriteTeamCardHolder extends HomeCardHolder<HomeCardState.FavouriteTeam> {

    private final View viewTeamColour;
    private final TextView tvName, tvPosition, tvPoints;
    private final TextView tvDriver1Name, tvDriver1Points, tvDriver2Name, tvDriver2Points;
    private final View driver2Row;
    private final View layoutH2h;
    private final TextView tvH2h;

    FavouriteTeamCardHolder(@NonNull View itemView) {
        super(itemView, HomeCardType.FAVOURITE_TEAM);
        viewTeamColour  = itemView.findViewById(R.id.view_team_colour);
        tvName          = itemView.findViewById(R.id.tv_team_name);
        tvPosition      = itemView.findViewById(R.id.tv_team_position);
        tvPoints        = itemView.findViewById(R.id.tv_team_points);
        tvDriver1Name   = itemView.findViewById(R.id.tv_driver1_name);
        tvDriver1Points = itemView.findViewById(R.id.tv_driver1_points);
        tvDriver2Name   = itemView.findViewById(R.id.tv_driver2_name);
        tvDriver2Points = itemView.findViewById(R.id.tv_driver2_points);
        driver2Row      = (View) tvDriver2Name.getParent();
        layoutH2h       = itemView.findViewById(R.id.layout_team_h2h);
        tvH2h           = itemView.findViewById(R.id.tv_team_h2h);
    }

    @Override
    boolean isClickable() {
        return true;
    }

    @Override
    void bind(@NonNull HomeCardState.FavouriteTeam state, @Nullable Map<String, String> headshots) {
        Context context = itemView.getContext();
        int colour = TeamColors.get(context, state.constructorId, state.name, null);
        viewTeamColour.setBackgroundColor(colour);
        tvName.setText(orEmpty(state.name));
        tvName.setTextColor(colour);

        String position = context.getString(R.string.home_position, state.position);
        String gap = state.position == 1
                ? context.getString(R.string.home_leader)
                : context.getString(R.string.home_gap_to_ahead,
                        HomeCardBuilder.formatPoints(state.gapToAhead), state.position - 1);
        tvPosition.setText(context.getString(R.string.home_position_and_gap, position, gap));
        tvPoints.setText(HomeCardBuilder.formatPoints(state.points));

        bindDriver(context, state.driverA, tvDriver1Name, tvDriver1Points);
        bindDriver(context, state.driverB, tvDriver2Name, tvDriver2Points);
        driver2Row.setVisibility(state.driverB != null ? View.VISIBLE : View.GONE);

        boolean hasPair = state.driverA != null && state.driverB != null;
        layoutH2h.setVisibility(hasPair ? View.VISIBLE : View.GONE);
        if (hasPair) {
            tvH2h.setText(context.getString(R.string.home_h2h_score, state.h2hA, state.h2hB));
        }
    }

    private static void bindDriver(Context context, @Nullable HomeCardState.DriverRow row,
                                   TextView name, TextView points) {
        name.setText(row != null ? orEmpty(row.name) : "");
        points.setText(row != null
                ? context.getString(R.string.home_points_value,
                        HomeCardBuilder.formatPoints(HomeCardBuilder.parsePoints(row.points)))
                : "");
    }
}
