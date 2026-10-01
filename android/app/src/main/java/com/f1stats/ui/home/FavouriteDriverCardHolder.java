package com.f1stats.ui.home;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.f1stats.R;
import com.f1stats.home.HomeCardType;
import com.f1stats.util.HeadToHead;

import java.util.Map;

/** FAVOURITE_DRIVER: standing, gap ahead, wins, podiums and a five-race form guide. */
class FavouriteDriverCardHolder extends HomeCardHolder<HomeCardState.FavouriteDriver> {

    private final View viewTeamColour;
    private final ImageView ivHeadshot;
    private final TextView tvName, tvTeam, tvPosition, tvGap, tvPoints, tvWins, tvPodiums;
    private final LinearLayout llForm;

    FavouriteDriverCardHolder(@NonNull View itemView) {
        super(itemView, HomeCardType.FAVOURITE_DRIVER);
        viewTeamColour = itemView.findViewById(R.id.view_team_colour);
        ivHeadshot     = itemView.findViewById(R.id.iv_headshot);
        tvName         = itemView.findViewById(R.id.tv_driver_name);
        tvTeam         = itemView.findViewById(R.id.tv_driver_team);
        tvPosition     = itemView.findViewById(R.id.tv_position);
        tvGap          = itemView.findViewById(R.id.tv_gap);
        tvPoints       = itemView.findViewById(R.id.tv_points);
        tvWins         = itemView.findViewById(R.id.tv_wins);
        tvPodiums      = itemView.findViewById(R.id.tv_podiums);
        llForm         = itemView.findViewById(R.id.ll_form);
    }

    @Override
    boolean isClickable() {
        return true;
    }

    @Override
    void bind(@NonNull HomeCardState.FavouriteDriver state, @Nullable Map<String, String> headshots) {
        Context context = itemView.getContext();
        tvName.setText(orEmpty(state.name));
        tvTeam.setText(orEmpty(state.team));
        applyTeamColour(viewTeamColour, state.constructorId, state.team);
        loadHeadshot(ivHeadshot, state.code, headshots);

        tvPosition.setText(context.getString(R.string.home_position, state.position));
        tvGap.setText(state.position == 1
                ? context.getString(R.string.home_leader)
                : context.getString(R.string.home_gap_to_ahead,
                        HomeCardBuilder.formatPoints(state.gapToAhead), state.position - 1));
        tvPoints.setText(HomeCardBuilder.formatPoints(state.points));
        tvWins.setText(String.valueOf(state.wins));
        tvPodiums.setText(String.valueOf(state.podiums));

        bindForm(context, state);
    }

    private void bindForm(Context context, HomeCardState.FavouriteDriver state) {
        llForm.removeAllViews();
        if (state.form.isEmpty()) {
            TextView none = new TextView(context);
            none.setText(R.string.home_form_none);
            none.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary));
            none.setTextSize(12f);
            llForm.addView(none);
            return;
        }
        float density = context.getResources().getDisplayMetrics().density;
        int padH = Math.round(8 * density);
        int padV = Math.round(4 * density);
        int gap  = Math.round(6 * density);
        int minWidth = Math.round(40 * density);
        for (HeadToHead.FormEntry entry : state.form) {
            TextView chip = new TextView(context);
            chip.setBackgroundResource(R.drawable.bg_form_chip);
            chip.setPadding(padH, padV, padH, padV);
            chip.setMinWidth(minWidth);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setTextSize(12f);
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);

            int colour;
            if (entry.didNotStart()) {
                chip.setText(R.string.home_form_dns);
                colour = R.color.text_tertiary;
            } else if (entry.isDnf() || entry.position <= 0) {
                chip.setText(R.string.home_form_dnf);
                colour = R.color.color_eliminated;
            } else {
                chip.setText(context.getString(R.string.home_position, entry.position));
                colour = entry.position <= 3 ? R.color.text_primary : R.color.text_secondary;
            }
            chip.setTextColor(ContextCompat.getColor(context, colour));
            if (entry.raceName != null) chip.setContentDescription(entry.raceName + ": " + chip.getText());

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(gap);
            llForm.addView(chip, lp);
        }
    }
}
