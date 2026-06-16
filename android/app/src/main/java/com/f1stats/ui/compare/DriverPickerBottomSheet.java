package com.f1stats.ui.compare;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.F1App;
import com.f1stats.R;
import com.f1stats.db.CachedDriver;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.concurrent.Executors;

public class DriverPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnDriverSelectedListener {
        void onDriverSelected(CachedDriver driver);
    }

    private static final String ARG_YEAR = "arg_year";

    private OnDriverSelectedListener listener;

    public static DriverPickerBottomSheet newInstance(int year) {
        DriverPickerBottomSheet sheet = new DriverPickerBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_YEAR, year);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnDriverSelectedListener) {
            listener = (OnDriverSelectedListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_driver_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int year = getArguments() != null ? getArguments().getInt(ARG_YEAR) : 2026;

        RecyclerView rv = view.findViewById(R.id.rv_drivers);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        DriverPickerAdapter adapter = new DriverPickerAdapter(driver -> {
            if (listener != null) listener.onDriverSelected(driver);
            dismiss();
        });
        rv.setAdapter(adapter);

        Handler mainHandler = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            List<CachedDriver> drivers = F1App.get().getDatabase().driverDao().getBySeason(year);
            drivers.sort((a, b) -> {
                if (a.lastName == null) return 1;
                if (b.lastName == null) return -1;
                return a.lastName.compareTo(b.lastName);
            });
            mainHandler.post(() -> adapter.setDrivers(drivers));
        });
    }
}
