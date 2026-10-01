package com.f1stats.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.f1stats.R;
import com.f1stats.home.HomeCardType;
import com.f1stats.util.TeamColors;

import java.util.Map;

/** CHAMPIONSHIP_SNAPSHOT: top five, plus the favourite highlighted when outside it. */
class SnapshotCardHolder extends HomeCardHolder<HomeCardState.Snapshot> {

    private final TextView tvLabel;
    private final LinearLayout llRows;

    SnapshotCardHolder(@NonNull View itemView) {
        super(itemView, HomeCardType.CHAMPIONSHIP_SNAPSHOT);
        tvLabel = itemView.findViewById(R.id.tv_label);
        llRows  = itemView.findViewById(R.id.ll_snapshot_rows);
    }

    @Override
    boolean isClickable() {
        return true;
    }

    @Override
    void bind(@NonNull HomeCardState.Snapshot state, @Nullable Map<String, String> headshots) {
        Context context = itemView.getContext();
        tvLabel.setText(context.getString(state.constructors
                ? R.string.home_snapshot_constructors_label
                : R.string.home_snapshot_drivers_label, state.year));

        llRows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (HomeCardState.Snapshot.SnapshotRow row : state.rows) {
            llRows.addView(rowView(inflater, context, row));
        }
        if (state.favourite != null) {
            TextView more = new TextView(context);
            more.setText(R.string.home_snapshot_more);
            more.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary));
            more.setPadding(Math.round(4 * context.getResources().getDisplayMetrics().density), 0, 0, 0);
            llRows.addView(more);
            llRows.addView(rowView(inflater, context, state.favourite));
        }
    }

    private View rowView(LayoutInflater inflater, Context context,
                         HomeCardState.Snapshot.SnapshotRow row) {
        View view = inflater.inflate(R.layout.item_home_snapshot_row, llRows, false);
        ((TextView) view.findViewById(R.id.tv_row_position))
                .setText(context.getString(R.string.home_position, row.position));
        view.findViewById(R.id.view_row_colour).setBackgroundColor(
                TeamColors.get(context, row.constructorId, row.team, null));
        TextView name = view.findViewById(R.id.tv_row_name);
        name.setText(orEmpty(row.name));
        ((TextView) view.findViewById(R.id.tv_row_gap)).setText(row.gapToLeader > 0
                ? context.getString(R.string.home_gap_to_leader,
                        HomeCardBuilder.formatPoints(row.gapToLeader))
                : "");
        ((TextView) view.findViewById(R.id.tv_row_points))
                .setText(HomeCardBuilder.formatPoints(row.points));
        if (row.highlighted) {
            view.setBackgroundResource(R.drawable.bg_form_chip);
            name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }
}
