package com.f1stats.ui.home;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.R;
import com.f1stats.home.HomeCardType;

import java.util.Map;

/** LAST_WINNER: winner of the most recent race. */
class LastWinnerCardHolder extends HomeCardHolder<HomeCardState.LastWinner> {

    private final TextView tvLastWinner, tvLastRaceTeam, tvLastRaceName;
    private final ImageView ivLastWinnerHeadshot;
    private final View viewLastWinnerColour;

    LastWinnerCardHolder(@NonNull View itemView) {
        super(itemView, HomeCardType.LAST_WINNER);
        tvLastWinner         = itemView.findViewById(R.id.tv_last_winner);
        tvLastRaceTeam       = itemView.findViewById(R.id.tv_last_race_team);
        tvLastRaceName       = itemView.findViewById(R.id.tv_last_race_name);
        ivLastWinnerHeadshot = itemView.findViewById(R.id.iv_last_winner_headshot);
        viewLastWinnerColour = itemView.findViewById(R.id.view_last_winner_colour);
    }

    @Override
    void bind(@NonNull HomeCardState.LastWinner state, @Nullable Map<String, String> headshots) {
        HomeCardState.DriverRow winner = state.winner;
        tvLastWinner.setText(winner != null ? orEmpty(winner.name) : "");
        tvLastRaceTeam.setText(winner != null ? orEmpty(winner.team) : "");
        applyTeamColour(viewLastWinnerColour,
                winner != null ? winner.constructorId : null,
                winner != null ? winner.team : null);
        loadHeadshot(ivLastWinnerHeadshot, winner != null ? winner.code : null, headshots);
        tvLastRaceName.setText(orEmpty(state.raceName));
    }
}
