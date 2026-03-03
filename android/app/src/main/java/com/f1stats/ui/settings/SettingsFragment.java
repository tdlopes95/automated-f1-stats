package com.f1stats.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.f1stats.F1App;
import com.f1stats.R;
import com.f1stats.SettingsManager;
import com.f1stats.api.F1ApiClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextInputEditText etUrl = view.findViewById(R.id.et_base_url);
        MaterialButton btnSave  = view.findViewById(R.id.btn_save_url);

        // Show current URL
        String currentUrl = SettingsManager.getInstance(requireContext()).getBaseUrl();
        etUrl.setText(currentUrl);

        btnSave.setOnClickListener(v -> {
            String newUrl = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
            if (newUrl.isEmpty()) {
                Toast.makeText(requireContext(), "URL cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newUrl.startsWith("http")) {
                Toast.makeText(requireContext(), "URL must start with http:// or https://", Toast.LENGTH_SHORT).show();
                return;
            }
            SettingsManager.getInstance(requireContext()).setBaseUrl(newUrl);
            F1ApiClient.reset(requireContext());
            Toast.makeText(requireContext(), "URL saved! Restart the app to apply.", Toast.LENGTH_LONG).show();
        });
    }
}