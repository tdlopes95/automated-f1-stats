package com.f1stats.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge helpers. targetSdk 35 forces edge-to-edge on Android 15+, so system bars
 * overlay content unless views pad themselves; on older versions the insets are zero.
 */
public final class SystemBarInsets {

    public static final int TYPES =
            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout();

    private SystemBarInsets() {}

    /** Pads the activity's root layout by all system bar / cutout insets. */
    public static void applyToContentRoot(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content != null && content.getChildCount() > 0) {
            applyPadding(content.getChildAt(0), true, true, true, true);
        }
    }

    /**
     * Adds the insets on the chosen sides to the view's XML padding, and consumes them so
     * descendants (e.g. BottomNavigationView) don't pad a second time.
     */
    public static void applyPadding(View view, boolean left, boolean top,
                                    boolean right, boolean bottom) {
        final int pl = view.getPaddingLeft();
        final int pt = view.getPaddingTop();
        final int pr = view.getPaddingRight();
        final int pb = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets i = windowInsets.getInsets(TYPES);
            v.setPadding(pl + (left ? i.left : 0), pt + (top ? i.top : 0),
                    pr + (right ? i.right : 0), pb + (bottom ? i.bottom : 0));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
