package com.f1stats.ui.compare;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.F1App;
import com.f1stats.R;
import com.f1stats.api.F1ApiClient;
import com.f1stats.data.F1Repository;
import com.f1stats.db.CachedDriver;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

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

        ProgressBar pbLoading = view.findViewById(R.id.pb_loading_drivers);

        DriverPickerAdapter adapter = new DriverPickerAdapter(driver -> {
            if (listener != null) listener.onDriverSelected(driver);
            dismiss();
        });
        rv.setAdapter(adapter);

        rv.setVisibility(View.GONE);
        pbLoading.setVisibility(View.VISIBLE);

        F1Repository repo = new F1Repository(
                F1App.get().getDatabase(),
                F1ApiClient.getInstance(F1App.get()).getService());

        repo.fetchDriversForSeason(year, new F1Repository.RepositoryCallback<List<CachedDriver>>() {
            @Override
            public void onSuccess(List<CachedDriver> drivers) {
                drivers.sort((a, b) -> {
                    if (a.lastName == null) return 1;
                    if (b.lastName == null) return -1;
                    return a.lastName.compareTo(b.lastName);
                });
                pbLoading.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);
                adapter.setDrivers(drivers);
            }
            @Override
            public void onError(String error) {
                pbLoading.setVisibility(View.GONE);
                rv.setVisibility(View.VISIBLE);
            }
        });
    }
}
