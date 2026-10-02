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
import com.google.android.material.textfield.TextInputLayout;

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
        TextInputLayout tilUrl  = view.findViewById(R.id.til_base_url);

        // Show current URL
        String currentUrl = SettingsManager.getInstance(requireContext()).getBaseUrl();
        etUrl.setText(currentUrl);

        btnSave.setOnClickListener(v -> {
            String newUrl = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
            if (newUrl.isEmpty()) {
                Toast.makeText(requireContext(), R.string.settings_url_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            String url = SettingsManager.normaliseUrl(newUrl);
            if (url == null) {
                if (SettingsManager.normaliseUrl(newUrl, true) != null) {
                    // A valid http:// URL in a release build
                    tilUrl.setError(getString(R.string.settings_url_https_required));
                } else {
                    Toast.makeText(requireContext(), BuildConfig.DEBUG
                            ? R.string.settings_url_invalid_any
                            : R.string.settings_url_invalid_https, Toast.LENGTH_SHORT).show();
                }
                return;
            }
            tilUrl.setError(null);
            etUrl.setText(url);
            SettingsManager.getInstance(requireContext()).setBaseUrl(url);
            // Repository and ViewModel fetch the service per call, so this applies immediately
            F1ApiClient.reset(requireContext());
            Toast.makeText(requireContext(), R.string.settings_url_saved, Toast.LENGTH_SHORT).show();
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
        bindLink(view, R.id.tv_credit_openmeteo, R.string.credit_openmeteo_url);
        bindLink(view, R.id.tv_credit_mpandroidchart, R.string.credit_mpandroidchart_url);
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