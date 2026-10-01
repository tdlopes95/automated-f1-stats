package com.f1stats.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;
import com.f1stats.home.HomeCardType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * One item per enabled Home card, in layout order; the view type is the card type. An optional
 * "Make Home yours" item sits above the cards.
 */
class HomeCardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface Callbacks extends HomeCardHolder.Callbacks {
        void onWelcomeAction();
        void onWelcomeDismiss();
    }

    private static final int VIEW_TYPE_WELCOME = -1;
    private static final long ID_WELCOME = -1;

    private final List<HomeCardType> types = new ArrayList<>();
    private final Map<HomeCardType, HomeCardState> states;
    private final Callbacks callbacks;
    private final Set<HomeCardHolder<?>> boundHolders =
            Collections.newSetFromMap(new WeakHashMap<>());
    private Map<String, String> headshots;
    private boolean showWelcome;

    HomeCardAdapter(@NonNull Map<HomeCardType, HomeCardState> states, @NonNull Callbacks callbacks) {
        this.states = states;
        this.callbacks = callbacks;
        setHasStableIds(true);
    }

    @SuppressWarnings("NotifyDataSetChanged")   // order and membership can both change
    void setContent(@NonNull List<HomeCardType> newTypes, boolean welcome) {
        if (types.equals(newTypes) && showWelcome == welcome) return;
        types.clear();
        types.addAll(newTypes);
        showWelcome = welcome;
        notifyDataSetChanged();
    }

    void notifyCardChanged(@NonNull HomeCardType type) {
        int index = types.indexOf(type);
        if (index >= 0) notifyItemChanged(index + offset());
    }

    void notifyAllCardsChanged() {
        if (!types.isEmpty()) notifyItemRangeChanged(offset(), types.size());
    }

    void setHeadshots(Map<String, String> headshots) {
        this.headshots = headshots;
        notifyCardChanged(HomeCardType.CHAMPIONSHIP_BATTLE);
        notifyCardChanged(HomeCardType.LAST_WINNER);
        notifyCardChanged(HomeCardType.FAVOURITE_DRIVER);
    }

    /** Stops countdowns and shimmers. Call when Home's view is destroyed. */
    void releaseAll() {
        for (HomeCardHolder<?> holder : new ArrayList<>(boundHolders)) holder.release();
        boundHolders.clear();
    }

    private int offset() {
        return showWelcome ? 1 : 0;
    }

    @Override
    public int getItemCount() {
        return types.size() + offset();
    }

    @Override
    public int getItemViewType(int position) {
        if (showWelcome && position == 0) return VIEW_TYPE_WELCOME;
        return types.get(position - offset()).ordinal();
    }

    @Override
    public long getItemId(int position) {
        if (showWelcome && position == 0) return ID_WELCOME;
        return types.get(position - offset()).ordinal();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_WELCOME) {
            View view = inflater.inflate(R.layout.item_home_card_welcome, parent, false);
            view.findViewById(R.id.btn_welcome_action).setOnClickListener(v -> callbacks.onWelcomeAction());
            view.findViewById(R.id.btn_welcome_dismiss).setOnClickListener(v -> callbacks.onWelcomeDismiss());
            return new RecyclerView.ViewHolder(view) {};
        }
        HomeCardType type = HomeCardType.values()[viewType];
        switch (type) {
            case NEXT_RACE:
                return new NextRaceCardHolder(inflate(inflater, parent, R.layout.item_home_card_next_race));
            case CHAMPIONSHIP_BATTLE:
                return new ChampionshipCardHolder(inflate(inflater, parent, R.layout.item_home_card_championship));
            case LAST_WINNER:
                return new LastWinnerCardHolder(inflate(inflater, parent, R.layout.item_home_card_last_winner));
            case FAVOURITE_DRIVER:
                return new FavouriteDriverCardHolder(inflate(inflater, parent, R.layout.item_home_card_favourite_driver));
            case FAVOURITE_TEAM:
                return new FavouriteTeamCardHolder(inflate(inflater, parent, R.layout.item_home_card_favourite_team));
            case PINNED_H2H:
                return new PinnedH2hCardHolder(inflate(inflater, parent, R.layout.item_home_card_pinned_h2h));
            case CHAMPIONSHIP_SNAPSHOT:
                return new SnapshotCardHolder(inflate(inflater, parent, R.layout.item_home_card_snapshot));
            case WEEKEND_WEATHER:
                return new WeatherCardHolder(inflate(inflater, parent, R.layout.item_home_card_weather));
            case NEWS:
                return new NewsCardHolder(inflate(inflater, parent, R.layout.item_home_card_news));
            case ON_THIS_DAY:
                return new OnThisDayCardHolder(inflate(inflater, parent, R.layout.item_home_card_on_this_day));
            default:
                throw new IllegalArgumentException("No Home card view for " + type);
        }
    }

    private static View inflate(LayoutInflater inflater, ViewGroup parent, int layout) {
        return inflater.inflate(layout, parent, false);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (!(holder instanceof HomeCardHolder)) return;   // welcome: static content
        int index = position - offset();
        HomeCardType type = types.get(index);
        HomeCardHolder cardHolder = (HomeCardHolder) holder;
        cardHolder.bindCard(states.get(type), headshots, callbacks);
        cardHolder.setBottomDividerVisible(isFlatSection(type)
                && (index == types.size() - 1 || !isFlatSection(types.get(index + 1))));
        boundHolders.add(cardHolder);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof HomeCardHolder) {
            ((HomeCardHolder<?>) holder).release();
            boundHolders.remove(holder);
        }
    }

    /**
     * Flat sections are full-width strips separated by 1dp dividers: each draws the divider
     * above itself, and the one below only when no flat section follows.
     */
    private static boolean isFlatSection(HomeCardType type) {
        return type != HomeCardType.NEXT_RACE;
    }
}
