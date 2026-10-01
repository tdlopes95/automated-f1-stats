package com.f1stats.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.f1stats.BuildConfig;
import com.f1stats.CustomizeHomeActivity;
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
            String url = SettingsManager.normaliseUrl(newUrl);
            if (url == null) {
                Toast.makeText(requireContext(), "Enter a valid http:// or https:// URL", Toast.LENGTH_SHORT).show();
                return;
            }
            etUrl.setText(url);
            SettingsManager.getInstance(requireContext()).setBaseUrl(url);
            // Repository and ViewModel fetch the service per call, so this applies immediately
            F1ApiClient.reset(requireContext());
            Toast.makeText(requireContext(), "URL saved", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.row_customize_home).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CustomizeHomeActivity.class)));

        bindAbout(view);
    }

    private void bindAbout(View view) {
        TextView tvVersion = view.findViewById(R.id.tv_app_version);
        tvVersion.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));

        bindLink(view, R.id.tv_credit_jolpica,   R.string.credit_jolpica_url);
        bindLink(view, R.id.tv_credit_openf1,    R.string.credit_openf1_url);
        bindLink(view, R.id.tv_credit_circuits,  R.string.credit_circuits_url);
        bindLink(view, R.id.tv_credit_flagpedia, R.string.credit_flagpedia_url);
    }

    private void bindLink(View root, int viewId, @StringRes int urlRes) {
        root.findViewById(viewId).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(urlRes)));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(requireContext(), R.string.about_no_browser, Toast.LENGTH_SHORT).show();
            }
        });
    }
}