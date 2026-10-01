package com.f1stats;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.f1stats.data.F1Repository;
import com.f1stats.db.CachedDriver;
import com.f1stats.home.HomeCardConfig;
import com.f1stats.home.HomeCardParams;
import com.f1stats.home.HomeCardType;
import com.f1stats.home.HomeLayoutStore;
import com.f1stats.models.ConstructorStanding;
import com.f1stats.ui.compare.DriverPickerBottomSheet;
import com.f1stats.ui.customize.CustomizeHomeAdapter;
import com.f1stats.util.SystemBarInsets;
import com.f1stats.util.TeamColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Show, hide and reorder Home cards, and set each card's options. Every change is saved to
 * HomeLayoutStore immediately.
 */
public class CustomizeHomeActivity extends AppCompatActivity
        implements DriverPickerBottomSheet.OnDriverSelectedListener {

    /** Optional HomeCardType name: open that card's options on start. */
    public static final String EXTRA_OPEN_OPTIONS = "extra_open_options";

    private static final String STATE_PENDING_PICK = "pending_pick";

    /** What the open driver picker is choosing. */
    private enum DriverPick { FAVOURITE, H2H_1, H2H_2 }

    private HomeLayoutStore store;
    private CustomizeHomeAdapter adapter;
    private ItemTouchHelper touchHelper;
    private int year;

    @Nullable private DriverPick pendingPick;
    /** The open pinned head-to-head dialog's views, refreshed after a pick. */
    @Nullable private View h2hDialogView;
    private boolean teamsLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customize_home);
        SystemBarInsets.applyToContentRoot(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.customize_home_title);
        }

        store = HomeLayoutStore.getInstance(this);
        year = SeasonHelper.getCurrentYear();
        if (savedInstanceState != null) {
            String pick = savedInstanceState.getString(STATE_PENDING_PICK);
            pendingPick = pick != null ? DriverPick.valueOf(pick) : null;
        }

        adapter = new CustomizeHomeAdapter(new CustomizeHomeAdapter.Listener() {
            @Override
            public void onStartDrag(@NonNull RecyclerView.ViewHolder holder) {
                touchHelper.startDrag(holder);
            }

            @Override
            public void onToggled(@NonNull HomeCardType type, boolean enabled) {
                save();
                // A card that can't show anything yet asks for its choice straight away
                if (enabled && HomeCardParams.needsChoice(store.getConfig(type))) openOptions(type);
            }

            @Override
            public void onOpenOptions(@NonNull HomeCardType type) {
                openOptions(type);
            }

            @Nullable
            @Override
            public CharSequence describeChoice(@NonNull HomeCardConfig config) {
                return CustomizeHomeActivity.this.describeChoice(config);
            }
        });

        RecyclerView rv = findViewById(R.id.rv_customize_cards);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;   // the drag handle starts drags
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                adapter.move(from.getAdapterPosition(), to.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {}

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder holder, int actionState) {
                super.onSelectedChanged(holder, actionState);
                if (holder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    holder.itemView.setAlpha(0.85f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder holder) {
                super.clearView(recyclerView, holder);
                holder.itemView.setAlpha(1f);
                save();   // drag finished
            }
        });
        touchHelper.attachToRecyclerView(rv);

        adapter.setItems(store.getLayout());

        if (savedInstanceState == null) {
            HomeCardType open = HomeCardType.fromName(getIntent().getStringExtra(EXTRA_OPEN_OPTIONS));
            if (open != null && open.hasOptions()) openOptions(open);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingPick != null) outState.putString(STATE_PENDING_PICK, pendingPick.name());
    }

    private void save() {
        store.save(adapter.getItems());
    }

    /** Saves one card's changed params and refreshes the list. */
    private void saveConfig(HomeCardConfig config) {
        store.updateConfig(config);
        adapter.setItems(store.getLayout());
    }

    // ── Options ───────────────────────────────────────────────────────────────

    private void openOptions(HomeCardType type) {
        switch (type) {
            case FAVOURITE_DRIVER:
                pickDriver(DriverPick.FAVOURITE);
                break;
            case FAVOURITE_TEAM:
                pickTeam();
                break;
            case PINNED_H2H:
                showPinnedH2hOptions();
                break;
            case CHAMPIONSHIP_SNAPSHOT:
                showSnapshotOptions();
                break;
            default:
                break;
        }
    }

    @Nullable
    private CharSequence describeChoice(HomeCardConfig config) {
        switch (config.getType()) {
            case FAVOURITE_DRIVER:
                return config.getParam(HomeCardParams.DRIVER_NAME);
            case FAVOURITE_TEAM:
                return config.getParam(HomeCardParams.TEAM_NAME);
            case PINNED_H2H:
                if (config.getBooleanParam(HomeCardParams.TEAMMATES_OF_FAVOURITE)) {
                    return getString(R.string.customize_choice_teammates);
                }
                String d1 = config.getParam(HomeCardParams.DRIVER_NAME_1);
                String d2 = config.getParam(HomeCardParams.DRIVER_NAME_2);
                return d1 != null && d2 != null ? getString(R.string.customize_choice_vs, d1, d2) : null;
            case CHAMPIONSHIP_SNAPSHOT:
                return getString(HomeCardParams.isConstructorsMode(config)
                        ? R.string.options_snapshot_constructors : R.string.options_snapshot_drivers);
            default:
                return null;
        }
    }

    // Driver picker (Jolpica identity, current season) ─────────────────────────

    private void pickDriver(DriverPick pick) {
        pendingPick = pick;
        DriverPickerBottomSheet.newInstance(year).show(getSupportFragmentManager(), "driver_picker");
    }

    @Override
    public void onDriverSelected(CachedDriver driver) {
        DriverPick pick = pendingPick;
        pendingPick = null;
        if (pick == null || driver.driverId == null || driver.driverId.isEmpty()) return;
        String name = displayName(driver);
        HomeCardConfig config;
        switch (pick) {
            case FAVOURITE:
                config = store.getConfig(HomeCardType.FAVOURITE_DRIVER);
                config.setParam(HomeCardParams.DRIVER_ID, driver.driverId);
                config.setParam(HomeCardParams.DRIVER_NAME, name);
                break;
            case H2H_1:
            case H2H_2:
                config = store.getConfig(HomeCardType.PINNED_H2H);
                boolean first = pick == DriverPick.H2H_1;
                config.setParam(first ? HomeCardParams.DRIVER_ID_1 : HomeCardParams.DRIVER_ID_2, driver.driverId);
                config.setParam(first ? HomeCardParams.DRIVER_NAME_1 : HomeCardParams.DRIVER_NAME_2, name);
                break;
            default:
                return;
        }
        saveConfig(config);
        bindH2hDialog();
    }

    private static String displayName(CachedDriver driver) {
        String first = driver.firstName != null ? driver.firstName : "";
        String last  = driver.lastName  != null ? driver.lastName  : "";
        String name = (first + " " + last).trim();
        return name.isEmpty() ? driver.driverId : name;
    }

    // Team list from constructor standings ────────────────────────────────────

    private void pickTeam() {
        if (teamsLoading) return;
        teamsLoading = true;
        F1Repository.getInstance(this).getConstructorStandings(year,
                new F1Repository.RepositoryCallback<Map<String, Object>>() {
                    @Override
                    public void onSuccess(Map<String, Object> data) {
                        teamsLoading = false;
                        if (isFinishing() || isDestroyed()) return;
                        showTeams(parseConstructors(data));
                    }

                    @Override
                    public void onError(String error) {
                        teamsLoading = false;
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(CustomizeHomeActivity.this, R.string.options_teams_error,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static List<ConstructorStanding> parseConstructors(Map<String, Object> data) {
        Object standings = data.get("standings");
        if (!(standings instanceof List)) return new ArrayList<>();
        Gson gson = new Gson();
        try {
            List<ConstructorStanding> list = gson.fromJson(gson.toJson(standings),
                    new TypeToken<List<ConstructorStanding>>(){}.getType());
            List<ConstructorStanding> out = new ArrayList<>();
            if (list != null) {
                for (ConstructorStanding c : list) if (c.getConstructor() != null) out.add(c);
            }
            return out;
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
    }

    private void showTeams(List<ConstructorStanding> teams) {
        if (teams.isEmpty()) {
            Toast.makeText(this, R.string.options_teams_error, Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayAdapter<ConstructorStanding> listAdapter = new ArrayAdapter<ConstructorStanding>(
                this, R.layout.item_team_option, teams) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = convertView != null ? convertView
                        : LayoutInflater.from(getContext()).inflate(R.layout.item_team_option, parent, false);
                ConstructorStanding team = getItem(position);
                String id = team.getConstructor().getConstructorId();
                String name = team.getConstructor().getName();
                ((TextView) view.findViewById(R.id.tv_team_option)).setText(name);
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(TeamColors.get(getContext(), id, name, null));
                view.findViewById(R.id.view_team_dot).setBackground(dot);
                return view;
            }
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.home_card_favourite_team_name)
                .setAdapter(listAdapter, (dialog, which) -> {
                    ConstructorStanding team = teams.get(which);
                    HomeCardConfig config = store.getConfig(HomeCardType.FAVOURITE_TEAM);
                    config.setParam(HomeCardParams.CONSTRUCTOR_ID, team.getConstructor().getConstructorId());
                    config.setParam(HomeCardParams.TEAM_NAME, team.getConstructor().getName());
                    saveConfig(config);
                })
                .setNegativeButton(R.string.customize_home_cancel, null)
                .show();
    }

    // Pinned head-to-head ─────────────────────────────────────────────────────

    private void showPinnedH2hOptions() {
        View view = getLayoutInflater().inflate(R.layout.dialog_pinned_h2h_options, null);
        h2hDialogView = view;
        view.findViewById(R.id.row_h2h_driver1).setOnClickListener(v -> pickDriver(DriverPick.H2H_1));
        view.findViewById(R.id.row_h2h_driver2).setOnClickListener(v -> pickDriver(DriverPick.H2H_2));
        MaterialSwitch teammates = view.findViewById(R.id.switch_h2h_teammates);
        teammates.setChecked(store.getConfig(HomeCardType.PINNED_H2H)
                .getBooleanParam(HomeCardParams.TEAMMATES_OF_FAVOURITE));
        teammates.setOnCheckedChangeListener((button, checked) -> {
            HomeCardConfig config = store.getConfig(HomeCardType.PINNED_H2H);
            config.setParam(HomeCardParams.TEAMMATES_OF_FAVOURITE, checked ? "true" : null);
            saveConfig(config);
            bindH2hDialog();
        });
        bindH2hDialog();

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.home_card_pinned_h2h_name)
                .setView(view)
                .setPositiveButton(R.string.options_done, null)
                .create();
        dialog.setOnDismissListener(d -> h2hDialogView = null);
        dialog.show();
    }

    private void bindH2hDialog() {
        View view = h2hDialogView;
        if (view == null) return;
        HomeCardConfig config = store.getConfig(HomeCardType.PINNED_H2H);
        boolean teammates = config.getBooleanParam(HomeCardParams.TEAMMATES_OF_FAVOURITE);
        bindDriverRow(view.findViewById(R.id.row_h2h_driver1), view.findViewById(R.id.tv_h2h_driver1),
                config.getParam(HomeCardParams.DRIVER_NAME_1), !teammates);
        bindDriverRow(view.findViewById(R.id.row_h2h_driver2), view.findViewById(R.id.tv_h2h_driver2),
                config.getParam(HomeCardParams.DRIVER_NAME_2), !teammates);
    }

    private void bindDriverRow(View row, TextView value, @Nullable String name, boolean enabled) {
        value.setText(name != null ? name : getString(R.string.options_not_set));
        row.setEnabled(enabled);
        row.setAlpha(enabled ? 1f : 0.4f);
    }

    // Championship snapshot ───────────────────────────────────────────────────

    private void showSnapshotOptions() {
        HomeCardConfig current = store.getConfig(HomeCardType.CHAMPIONSHIP_SNAPSHOT);
        String[] labels = {
                getString(R.string.options_snapshot_drivers),
                getString(R.string.options_snapshot_constructors)};
        int checked = HomeCardParams.isConstructorsMode(current) ? 1 : 0;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.home_card_snapshot_name)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    HomeCardConfig config = store.getConfig(HomeCardType.CHAMPIONSHIP_SNAPSHOT);
                    config.setParam(HomeCardParams.MODE, which == 1
                            ? HomeCardParams.MODE_CONSTRUCTORS : HomeCardParams.MODE_DRIVERS);
                    saveConfig(config);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.customize_home_cancel, null)
                .show();
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    private void confirmReset() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.customize_home_reset)
                .setMessage(R.string.customize_home_reset_message)
                .setNegativeButton(R.string.customize_home_cancel, null)
                .setPositiveButton(R.string.customize_home_reset_confirm, (dialog, which) -> {
                    store.resetToDefault();
                    adapter.setItems(store.getLayout());
                })
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.customize_home_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.action_reset_home) {
            confirmReset();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
