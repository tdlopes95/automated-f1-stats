package com.f1stats.ui.home;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.R;
import com.f1stats.home.HomeCardType;

import java.util.Map;

/** CHAMPIONSHIP_BATTLE: leader vs P2 with the points gap and an insight line. */
class ChampionshipCardHolder extends HomeCardHolder<HomeCardState.Championship> {

    private final TextView tvLeaderTitle, tvLeaderName, tvLeaderTeam, tvLeaderPoints;
    private final View viewLeaderColour;
    private final ImageView ivLeaderHeadshot;
    private final TextView tvChampGap, tvChampInsight;
    private final View layoutChampGap;
    private final TextView tvP2Name, tvP2Team, tvP2Points;
    private final View viewP2Colour;
    private final ImageView ivP2Headshot;
    private final View layoutP2;

    ChampionshipCardHolder(@NonNull View itemView) {
        super(itemView, HomeCardType.CHAMPIONSHIP_BATTLE);
        tvLeaderTitle    = itemView.findViewById(R.id.tv_leader_title);
        tvLeaderName     = itemView.findViewById(R.id.tv_leader_name);
        tvLeaderTeam     = itemView.findViewById(R.id.tv_leader_team);
        tvLeaderPoints   = itemView.findViewById(R.id.tv_leader_points);
        viewLeaderColour = itemView.findViewById(R.id.view_leader_colour);
        ivLeaderHeadshot = itemView.findViewById(R.id.iv_leader_headshot);
        tvChampGap       = itemView.findViewById(R.id.tv_champ_gap);
        tvChampInsight   = itemView.findViewById(R.id.tv_champ_insight);
        layoutChampGap   = itemView.findViewById(R.id.layout_champ_gap);
        tvP2Name         = itemView.findViewById(R.id.tv_p2_name);
        tvP2Team         = itemView.findViewById(R.id.tv_p2_team);
        tvP2Points       = itemView.findViewById(R.id.tv_p2_points);
        viewP2Colour     = itemView.findViewById(R.id.view_p2_colour);
        ivP2Headshot     = itemView.findViewById(R.id.iv_p2_headshot);
        layoutP2         = itemView.findViewById(R.id.layout_p2);
    }

    @Override
    void bind(@NonNull HomeCardState.Championship state, @Nullable Map<String, String> headshots) {
        tvLeaderTitle.setText(state.seasonStarted
                ? R.string.home_championship_battle : R.string.home_last_season_champion);

        HomeCardState.DriverRow leader = state.leader;
        if (leader != null) {
            tvLeaderName.setText(orEmpty(leader.name));
            tvLeaderTeam.setText(orEmpty(leader.team));
            tvLeaderPoints.setText(stripPtsSuffix(leader.points));
            applyTeamColour(viewLeaderColour, leader.constructorId, leader.team);
            loadHeadshot(ivLeaderHeadshot, leader.code, headshots);
        }

        if (state.gap > 0) {
            tvChampGap.setText(String.valueOf((int) state.gap));
            layoutChampGap.setVisibility(View.VISIBLE);
            boolean hasInsight = state.insight != null && !state.insight.isEmpty();
            tvChampInsight.setText(hasInsight ? state.insight : "");
            tvChampInsight.setVisibility(hasInsight ? View.VISIBLE : View.GONE);
        } else {
            layoutChampGap.setVisibility(View.GONE);
            tvChampInsight.setVisibility(View.GONE);
        }

        HomeCardState.DriverRow p2 = state.p2;
        if (p2 != null) {
            tvP2Name.setText(orEmpty(p2.name));
            tvP2Team.setText(orEmpty(p2.team));
            tvP2Points.setText(stripPtsSuffix(p2.points));
            applyTeamColour(viewP2Colour, p2.constructorId, p2.team);
            loadHeadshot(ivP2Headshot, p2.code, headshots);
            layoutP2.setVisibility(View.VISIBLE);
        } else {
            layoutP2.setVisibility(View.GONE);
        }
    }
}
