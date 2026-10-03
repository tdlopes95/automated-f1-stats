package com.f1stats.ui.settings;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.f1stats.api.RequestStats;
import com.f1stats.home.HomeLayoutStore;
import com.f1stats.notifications.NotificationSettings;
import com.f1stats.notifications.Notifications;
import com.f1stats.notifications.ReminderPlanner;
import com.f1stats.notifications.ReminderPlanner.Session;
import com.f1stats.notifications.ReminderPlanner.SessionKind;
import com.f1stats.notifications.ReminderScheduler;
import com.f1stats.notifications.ResultsWorker;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class SettingsFragment extends Fragment {

    private static final int[] LEAD_BUTTON_IDS = {R.id.btn_lead_15, R.id.btn_lead_30, R.id.btn_lead_60};

    private MaterialSwitch switchReminders;
    private MaterialSwitch switchResults;
    private View reminderOptions;
    private View exactAlarmsRow;
    private TextView resultsDescription;
    private View resultsRow;
    // True while refreshNotificationViews sets the switches, so their listeners stay quiet
    private boolean binding;

    // Turning on either switch asks for POST_NOTIFICATIONS (Android 13+); denial turns both off
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    ReminderScheduler.rescheduleAsync(requireContext());
                    return;
                }
                NotificationSettings settings = NotificationSettings.getInstance(requireContext());
                settings.setRemindersEnabled(false);
                settings.setResultsEnabled(false);
                ReminderScheduler.rescheduleAsync(requireContext());
                if (getView() != null) refreshNotificationViews();
                Toast.makeText(requireContext(), R.string.settings_notifications_denied,
                        Toast.LENGTH_LONG).show();
            });

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

        bindNotifications(view);
        bindAbout(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Exact alarm access or the favourites may have changed while away
        if (getView() != null) refreshNotificationViews();
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private void bindNotifications(View view) {
        Context context = requireContext();
        NotificationSettings settings = NotificationSettings.getInstance(context);

        switchReminders = view.findViewById(R.id.switch_reminders);
        switchResults = view.findViewById(R.id.switch_results);
        reminderOptions = view.findViewById(R.id.group_reminder_options);
        exactAlarmsRow = view.findViewById(R.id.row_exact_alarms);
        resultsDescription = view.findViewById(R.id.tv_results_description);
        resultsRow = view.findViewById(R.id.row_results);

        refreshNotificationViews();

        switchReminders.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            settings.setRemindersEnabled(checked);
            onNotificationSettingChanged(checked);
        });
        switchResults.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            settings.setResultsEnabled(checked);
            onNotificationSettingChanged(checked);
        });

        MaterialButtonToggleGroup leadGroup = view.findViewById(R.id.toggle_lead_time);
        int lead = settings.getLeadMinutes();
        for (int i = 0; i < LEAD_BUTTON_IDS.length; i++) {
            if (NotificationSettings.LEAD_MINUTE_OPTIONS[i] == lead) leadGroup.check(LEAD_BUTTON_IDS[i]);
        }
        leadGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            for (int i = 0; i < LEAD_BUTTON_IDS.length; i++) {
                if (LEAD_BUTTON_IDS[i] == checkedId) {
                    settings.setLeadMinutes(NotificationSettings.LEAD_MINUTE_OPTIONS[i]);
                }
            }
            ReminderScheduler.rescheduleAsync(context);
        });

        Set<SessionKind> sessions = settings.getSessions();
        bindSessionBox(view, R.id.cb_session_practice, SessionKind.PRACTICE, sessions);
        bindSessionBox(view, R.id.cb_session_qualifying, SessionKind.QUALIFYING, sessions);
        bindSessionBox(view, R.id.cb_session_sprint_qualifying, SessionKind.SPRINT_QUALIFYING, sessions);
        bindSessionBox(view, R.id.cb_session_sprint, SessionKind.SPRINT, sessions);
        bindSessionBox(view, R.id.cb_session_race, SessionKind.RACE, sessions);

        view.findViewById(R.id.btn_exact_alarms).setOnClickListener(v -> openExactAlarmSettings());

        if (BuildConfig.DEBUG) {
            view.findViewById(R.id.group_notification_debug).setVisibility(View.VISIBLE);
            view.findViewById(R.id.btn_debug_test_reminder).setOnClickListener(v -> sendTestReminder());
            view.findViewById(R.id.btn_debug_results_check).setOnClickListener(v -> runResultsCheck());
            view.findViewById(R.id.btn_debug_request_stats).setOnClickListener(v -> {
                RequestStats.log("on demand");
                Toast.makeText(requireContext(), R.string.settings_debug_request_stats_logged,
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void bindSessionBox(View root, int id, SessionKind kind, Set<SessionKind> enabled) {
        MaterialCheckBox box = root.findViewById(id);
        box.setChecked(enabled.contains(kind));
        box.setOnCheckedChangeListener((button, checked) -> {
            NotificationSettings.getInstance(requireContext()).setSessionEnabled(kind, checked);
            ReminderScheduler.rescheduleAsync(requireContext());
        });
    }

    private void onNotificationSettingChanged(boolean turnedOn) {
        refreshNotificationViews();
        if (turnedOn && Notifications.needsPermission(requireContext())) {
            // Rescheduled once the permission result arrives
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        ReminderScheduler.rescheduleAsync(requireContext());
    }

    private void refreshNotificationViews() {
        Context context = requireContext();
        NotificationSettings settings = NotificationSettings.getInstance(context);
        boolean reminders = settings.isRemindersEnabled();
        binding = true;
        switchReminders.setChecked(reminders);
        switchResults.setChecked(settings.isResultsEnabled());
        binding = false;
        reminderOptions.setVisibility(reminders ? View.VISIBLE : View.GONE);
        exactAlarmsRow.setVisibility(reminders && !ReminderScheduler.canScheduleExactAlarms(context)
                ? View.VISIBLE : View.GONE);

        HomeLayoutStore store = HomeLayoutStore.getInstance(context);
        boolean hasFavourite = store.getFavouriteDriverId() != null
                || store.getFavouriteConstructorId() != null;
        resultsDescription.setText(hasFavourite
                ? R.string.settings_results_description : R.string.settings_results_no_favourite);
        if (hasFavourite) {
            // The row itself does nothing; tapping it toggles the switch
            resultsRow.setOnClickListener(v -> switchResults.performClick());
        } else {
            resultsRow.setOnClickListener(v ->
                    startActivity(new Intent(context, CustomizeHomeActivity.class)));
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + requireContext().getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + requireContext().getPackageName())));
        }
    }

    // ── Debug ─────────────────────────────────────────────────────────────────

    private void sendTestReminder() {
        Context context = requireContext().getApplicationContext();
        if (!Notifications.canPost(context)) {
            Toast.makeText(context, R.string.settings_debug_notifications_off, Toast.LENGTH_SHORT).show();
            return;
        }
        runWithSessions(sessions -> {
            Session next = ReminderPlanner.nextSession(sessions, System.currentTimeMillis());
            if (next == null) {
                Toast.makeText(context, R.string.settings_debug_no_session, Toast.LENGTH_SHORT).show();
                return;
            }
            Notifications.showReminder(context, next.year, next.round, next.name, next.raceName,
                    next.startMillis);
        });
    }

    private void runResultsCheck() {
        Context context = requireContext().getApplicationContext();
        HomeLayoutStore store = HomeLayoutStore.getInstance(context);
        if (store.getFavouriteDriverId() == null && store.getFavouriteConstructorId() == null) {
            Toast.makeText(context, R.string.settings_debug_no_favourite, Toast.LENGTH_SHORT).show();
            return;
        }
        runWithSessions(sessions -> {
            Session race = ReminderPlanner.lastCompletedRace(sessions, System.currentTimeMillis());
            if (race == null) {
                Toast.makeText(context, R.string.settings_debug_no_race, Toast.LENGTH_SHORT).show();
                return;
            }
            ResultsWorker.runNow(context, race);
            Toast.makeText(context, context.getString(R.string.settings_debug_results_queued,
                    race.raceName), Toast.LENGTH_SHORT).show();
        });
    }

    /** Loads the cached schedule's sessions off the main thread, then calls back on it. */
    private void runWithSessions(Consumer<List<Session>> onLoaded) {
        Context context = requireContext().getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<Session> sessions = ReminderScheduler.loadSessions(context);
            main.post(() -> onLoaded.accept(sessions));
        }).start();
    }

    private void bindAbout(View view) {
        TextView tvVersion = view.findViewById(R.id.tv_app_version);
        tvVersion.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));

        bindLink(view, R.id.tv_credit_jolpica,   R.string.credit_jolpica_url);
        bindLink(view, R.id.tv_credit_openf1,    R.string.credit_openf1_url);
        bindLink(view, R.id.tv_credit_circuits,  R.string.credit_circuits_url);
        bindLink(view, R.id.tv_credit_multiviewer, R.string.credit_multiviewer_url);
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