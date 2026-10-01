package com.f1stats.ui.home;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.R;
import com.f1stats.home.HomeCardType;
import com.f1stats.util.HeadToHead;
import com.f1stats.util.TeamColors;

import java.util.Map;

/** PINNED_H2H: a compact compare screen (race H2H, points, wins, podiums). */
class PinnedH2hCardHolder extends HomeCardHolder<HomeCardState.PinnedH2h> {

    private final TextView tvLabel;
    private final View viewD1Colour, viewD2Colour;
    private final TextView tvD1Name, tvD1Team, tvD2Name, tvD2Team;
    private final TextView tvScore;
    private final View h2hBar1, h2hBar2;
    private final StatRow rowPoints, rowWins, rowPodiums;

    /** One include of item_stat_compare_row. */
    private static class StatRow {
        final TextView label, value1, value2;
        final View bar1, bar2;

        StatRow(View root, int labelRes) {
            label  = root.findViewById(R.id.tv_stat_label);
            value1 = root.findViewById(R.id.tv_d1_value);
            value2 = root.findViewById(R.id.tv_d2_value);
            bar1   = root.findViewById(R.id.view_d1_bar);
            bar2   = root.findViewById(R.id.view_d2_bar);
            label.setText(labelRes);
            root.setPadding(0, root.getPaddingTop() / 2, 0, root.getPaddingBottom() / 2);
        }
    }

    PinnedH2hCardHolder(@NonNull View itemView) {
        super(itemView, HomeCardType.PINNED_H2H);
        tvLabel      = itemView.findViewById(R.id.tv_label);
        viewD1Colour = itemView.findViewById(R.id.view_d1_colour);
        viewD2Colour = itemView.findViewById(R.id.view_d2_colour);
        tvD1Name     = itemView.findViewById(R.id.tv_d1_name);
        tvD1Team     = itemView.findViewById(R.id.tv_d1_team);
        tvD2Name     = itemView.findViewById(R.id.tv_d2_name);
        tvD2Team     = itemView.findViewById(R.id.tv_d2_team);
        tvScore      = itemView.findViewById(R.id.tv_h2h_score);
        h2hBar1      = itemView.findViewById(R.id.view_h2h_d1_bar);
        h2hBar2      = itemView.findViewById(R.id.view_h2h_d2_bar);
        rowPoints    = new StatRow(itemView.findViewById(R.id.row_points), R.string.home_pts);
        rowWins      = new StatRow(itemView.findViewById(R.id.row_wins), R.string.home_wins);
        rowPodiums   = new StatRow(itemView.findViewById(R.id.row_podiums), R.string.home_podiums);
    }

    @Override
    boolean isClickable() {
        return true;
    }

    @Override
    void bind(@NonNull HomeCardState.PinnedH2h state, @Nullable Map<String, String> headshots) {
        Context context = itemView.getContext();
        tvLabel.setText(context.getString(R.string.home_pinned_h2h_label, state.year));

        HomeCardState.PinnedH2h.Side d1 = state.driver1;
        HomeCardState.PinnedH2h.Side d2 = state.driver2;
        int colour1 = TeamColors.get(context, d1.constructorId, d1.team, null);
        int colour2 = TeamColors.get(context, d2.constructorId, d2.team, null);

        viewD1Colour.setBackgroundColor(colour1);
        viewD2Colour.setBackgroundColor(colour2);
        tvD1Name.setText(orEmpty(d1.name));
        tvD2Name.setText(orEmpty(d2.name));
        tvD1Team.setText(orEmpty(d1.team));
        tvD2Team.setText(orEmpty(d2.team));

        HeadToHead.DriverStats s1 = d1.stats;
        HeadToHead.DriverStats s2 = d2.stats;
        tvScore.setText(context.getString(R.string.home_h2h_score, s1.h2hWins, s2.h2hWins));
        setBars(h2hBar1, h2hBar2, s1.h2hWins, s2.h2hWins, colour1, colour2);

        bindRow(rowPoints, s1.points, s2.points, HomeCardBuilder.formatPoints(s1.points),
                HomeCardBuilder.formatPoints(s2.points), colour1, colour2);
        bindRow(rowWins, s1.wins, s2.wins, String.valueOf(s1.wins), String.valueOf(s2.wins),
                colour1, colour2);
        bindRow(rowPodiums, s1.podiums, s2.podiums, String.valueOf(s1.podiums),
                String.valueOf(s2.podiums), colour1, colour2);
    }

    private static void bindRow(StatRow row, double v1, double v2, String label1, String label2,
                                int colour1, int colour2) {
        row.value1.setText(label1);
        row.value2.setText(label2);
        setBars(row.bar1, row.bar2, v1, v2, colour1, colour2);
    }

    /** Same split as the compare screen: proportional, even when both are zero. */
    private static void setBars(View bar1, View bar2, double v1, double v2, int colour1, int colour2) {
        double total = v1 + v2;
        float w1 = total == 0 ? 1f : (float) (v1 / total);
        float w2 = total == 0 ? 1f : (float) (v2 / total);
        setBar(bar1, w1, colour1);
        setBar(bar2, w2, colour2);
    }

    private static void setBar(View bar, float weight, int colour) {
        bar.setBackgroundColor(colour);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bar.getLayoutParams();
        lp.weight = weight;
        bar.setLayoutParams(lp);
    }
}
