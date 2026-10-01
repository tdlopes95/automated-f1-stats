package com.f1stats.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.DateHelper;
import com.f1stats.R;
import com.f1stats.home.HomeCardType;
import com.f1stats.models.OnThisDayResponse;
import com.f1stats.util.TeamColors;

import java.util.Map;

/** ON_THIS_DAY: Grand Prix winners from this week in past seasons. */
class OnThisDayCardHolder extends HomeCardHolder<HomeCardState.OnThisDay> {

    private final LinearLayout llItems;
    private final View tvEmpty;

    OnThisDayCardHolder(@NonNull View itemView) {
        super(itemView, HomeCardType.ON_THIS_DAY);
        llItems = itemView.findViewById(R.id.ll_history_items);
        tvEmpty = itemView.findViewById(R.id.tv_history_empty);
    }

    @Override
    void bind(@NonNull HomeCardState.OnThisDay state, @Nullable Map<String, String> headshots) {
        Context context = itemView.getContext();
        llItems.removeAllViews();
        boolean empty = state.items.isEmpty();
        llItems.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(context);
        for (OnThisDayResponse.Item item : state.items) {
            View row = inflater.inflate(R.layout.item_on_this_day, llItems, false);
            String date = DateHelper.formatLocalDate(item.date);
            ((TextView) row.findViewById(R.id.tv_history_meta)).setText(item.yearsAgo > 0
                    ? context.getResources().getQuantityString(R.plurals.history_years_ago,
                            item.yearsAgo, item.yearsAgo, date)
                    : context.getString(R.string.history_this_season, date));
            ((TextView) row.findViewById(R.id.tv_history_race)).setText(orEmpty(item.raceName));
            ((TextView) row.findViewById(R.id.tv_history_winner)).setText(orEmpty(item.driverName));
            ((TextView) row.findViewById(R.id.tv_history_team)).setText(orEmpty(item.constructorName));
            row.findViewById(R.id.view_history_team_colour).setBackgroundColor(
                    TeamColors.get(context, item.constructorId, item.constructorName, null));
            row.setOnClickListener(v -> {
                if (callbacks != null) callbacks.onOpenHistoryRace(item);
            });
            llItems.addView(row);
        }
    }
}
