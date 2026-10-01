package com.f1stats.ui.customize;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.R;
import com.f1stats.home.HomeCardConfig;
import com.f1stats.home.HomeCardType;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Rows on the Customize home screen: drag handle, name, description, options, show/hide. */
public class CustomizeHomeAdapter extends RecyclerView.Adapter<CustomizeHomeAdapter.RowHolder> {

    public interface Listener {
        void onStartDrag(@NonNull RecyclerView.ViewHolder holder);
        /** A switch was toggled; the adapter's list already reflects it. */
        void onToggled(@NonNull HomeCardType type, boolean enabled);
        void onOpenOptions(@NonNull HomeCardType type);
        /** The card's current choice (e.g. the favourite driver), or null for its description. */
        @Nullable CharSequence describeChoice(@NonNull HomeCardConfig config);
    }

    private final List<HomeCardConfig> items = new ArrayList<>();
    private final Listener listener;

    public CustomizeHomeAdapter(@NonNull Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    @SuppressLint("NotifyDataSetChanged")   // whole layout replaced (initial load, reset)
    public void setItems(@NonNull List<HomeCardConfig> layout) {
        items.clear();
        items.addAll(layout);
        notifyDataSetChanged();
    }

    @NonNull
    public List<HomeCardConfig> getItems() {
        return new ArrayList<>(items);
    }

    public void move(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= items.size() || to >= items.size()) return;
        if (from < to) {
            for (int i = from; i < to; i++) Collections.swap(items, i, i + 1);
        } else {
            for (int i = from; i > to; i--) Collections.swap(items, i, i - 1);
        }
        notifyItemMoved(from, to);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getType().ordinal();
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_customize_home_card, parent, false);
        return new RowHolder(view);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        HomeCardConfig config = items.get(position);
        HomeCardType type = config.getType();

        holder.name.setText(type.getNameRes());
        CharSequence choice = listener.describeChoice(config);
        if (choice != null) holder.description.setText(choice);
        else                holder.description.setText(type.getDescriptionRes());

        holder.options.setVisibility(type.hasOptions() ? View.VISIBLE : View.GONE);
        holder.options.setOnClickListener(type.hasOptions()
                ? v -> listener.onOpenOptions(type) : null);

        holder.enabled.setOnCheckedChangeListener(null);
        holder.enabled.setChecked(config.isEnabled());
        holder.enabled.setContentDescription(holder.itemView.getContext().getString(type.getNameRes()));
        holder.enabled.setOnCheckedChangeListener((button, checked) -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            items.get(pos).setEnabled(checked);
            listener.onToggled(items.get(pos).getType(), checked);
        });

        // Drag starts from the handle only, so the switch and scrolling stay usable
        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                listener.onStartDrag(holder);
            }
            return false;
        });
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final View dragHandle;
        final TextView name;
        final TextView description;
        final View options;
        final MaterialSwitch enabled;

        RowHolder(@NonNull View itemView) {
            super(itemView);
            dragHandle  = itemView.findViewById(R.id.iv_drag_handle);
            name        = itemView.findViewById(R.id.tv_card_name);
            description = itemView.findViewById(R.id.tv_card_description);
            options     = itemView.findViewById(R.id.btn_card_options);
            enabled     = itemView.findViewById(R.id.switch_card_enabled);
        }
    }
}
