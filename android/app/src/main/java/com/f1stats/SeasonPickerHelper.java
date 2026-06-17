package com.f1stats;

import android.content.Context;
import android.widget.NumberPicker;

import androidx.appcompat.app.AlertDialog;

public class SeasonPickerHelper {

    public interface OnYearSelected {
        void onYearSelected(int year);
    }

    public static void showPicker(Context context, int currentYear, OnYearSelected listener) {
        int maxYear = SeasonHelper.getCurrentYear();

        NumberPicker picker = new NumberPicker(context);
        picker.setMinValue(1950);
        picker.setMaxValue(maxYear);
        picker.setValue(Math.min(Math.max(currentYear, 1950), maxYear));
        picker.setWrapSelectorWheel(false);

        new AlertDialog.Builder(context)
                .setTitle("Select Season")
                .setView(picker)
                .setPositiveButton("OK", (d, which) -> listener.onYearSelected(picker.getValue()))
                .setNegativeButton("Cancel", null)
                .show();
    }
}
