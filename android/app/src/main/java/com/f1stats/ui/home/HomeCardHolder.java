package com.f1stats.ui.home;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;
import com.f1stats.R;
import com.f1stats.home.HomeCardType;
import com.f1stats.util.TeamColors;

import java.util.Map;

/**
 * Base for Home card view holders. Every card layout has a {@code card_content} container and
 * the {@code view_home_card_status} include. Until a card has data the status replaces its
 * content: a shimmer while loading, a message with retry on error, or a prompt (e.g. "Choose
 * a driver") with an action.
 */
abstract class HomeCardHolder<S extends HomeCardState> extends RecyclerView.ViewHolder {

    interface Callbacks {
        void onRetry(@NonNull HomeCardType type);
        /** The prompt's action: open the options for {@code target}. */
        void onPromptAction(@NonNull HomeCardType target);
        void onCardClick(@NonNull HomeCardType type);
    }

    private final HomeCardType type;
    private final View content;
    private final View status;
    private final ShimmerFrameLayout statusShimmer;
    private final View statusMessage;
    private final TextView statusText;
    private final MaterialButton statusAction;
    @Nullable private final View dividerBottom;
    /** The tappable area: card_root when the layout has one. */
    private final View clickTarget;

    HomeCardHolder(@NonNull View itemView, @NonNull HomeCardType type) {
        super(itemView);
        this.type = type;
        content        = itemView.findViewById(R.id.card_content);
        status         = itemView.findViewById(R.id.layout_card_status);
        statusShimmer  = itemView.findViewById(R.id.shimmer_card_status);
        statusMessage  = itemView.findViewById(R.id.layout_card_message);
        statusText     = itemView.findViewById(R.id.tv_card_status);
        statusAction   = itemView.findViewById(R.id.btn_card_retry);
        dividerBottom  = itemView.findViewById(R.id.divider_bottom);
        View root      = itemView.findViewById(R.id.card_root);
        clickTarget    = root != null ? root : itemView;
    }

    final void bindCard(@NonNull S state, @Nullable Map<String, String> headshots,
                        @NonNull Callbacks callbacks) {
        Context context = itemView.getContext();
        boolean clickable = false;

        if (state.promptMessage != null) {
            onNoData();
            showMessage(state.promptMessage, state.promptAction,
                    v -> callbacks.onPromptAction(state.promptTarget));
        } else if (state.hasData) {
            stopShimmer();
            status.setVisibility(View.GONE);
            content.setVisibility(View.VISIBLE);
            bind(state, headshots);
            clickable = isClickable();
        } else if (state.status == HomeCardState.Status.ERROR) {
            onNoData();
            showMessage(context.getString(R.string.home_card_error, context.getString(type.getNameRes())),
                    context.getString(R.string.home_retry), v -> callbacks.onRetry(type));
        } else {
            onNoData();
            content.setVisibility(View.GONE);
            status.setVisibility(View.VISIBLE);
            statusMessage.setVisibility(View.GONE);
            statusShimmer.setVisibility(View.VISIBLE);
            statusShimmer.startShimmer();
        }

        clickTarget.setOnClickListener(clickable ? v -> callbacks.onCardClick(type) : null);
        clickTarget.setClickable(clickable);
    }

    private void showMessage(String message, String action, View.OnClickListener onAction) {
        stopShimmer();
        content.setVisibility(View.GONE);
        status.setVisibility(View.VISIBLE);
        statusMessage.setVisibility(View.VISIBLE);
        statusText.setText(message);
        statusAction.setText(action);
        statusAction.setOnClickListener(onAction);
    }

    private void stopShimmer() {
        statusShimmer.stopShimmer();
        statusShimmer.setVisibility(View.GONE);
    }

    /** Whether tapping a card with data does something. */
    boolean isClickable() {
        return false;
    }

    /** Renders a card that has data. */
    abstract void bind(@NonNull S state, @Nullable Map<String, String> headshots);

    /** Called instead of {@link #bind} while the card has no data; stop anything running. */
    void onNoData() {}

    /** Called when the view is recycled or Home's view is destroyed. */
    void release() {
        stopShimmer();
    }

    void setBottomDividerVisible(boolean visible) {
        if (dividerBottom != null) dividerBottom.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    void applyTeamColour(View strip, String constructorId, String teamName) {
        if (strip == null) return;
        if (teamName == null) {
            strip.setBackgroundResource(R.color.team_default);
            return;
        }
        strip.setBackgroundColor(TeamColors.get(itemView.getContext(), constructorId, teamName, null));
    }

    static void loadHeadshot(ImageView iv, String code, Map<String, String> headshots) {
        String url = code != null && headshots != null ? headshots.get(code) : null;
        if (url != null && !url.isEmpty()) {
            Glide.with(iv).load(url).circleCrop().into(iv);
        } else {
            Glide.with(iv).clear(iv);
        }
    }

    static String orEmpty(String s) {
        return s != null ? s : "";
    }

    static String stripPtsSuffix(String pts) {
        if (pts == null) return "";
        return pts.replace(" pts", "").trim();
    }
}
