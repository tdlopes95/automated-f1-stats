package com.f1stats;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.f1stats.api.F1ApiClient;
import com.f1stats.data.F1Repository;
import com.f1stats.db.CachedDriver;
import com.f1stats.db.CachedResult;
import com.f1stats.models.RaceResult;
import com.f1stats.ui.compare.DriverPickerBottomSheet;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class CompareDriversActivity extends AppCompatActivity
        implements DriverPickerBottomSheet.OnDriverSelectedListener {

    public static final String EXTRA_YEAR = "extra_year";

    private int year;
    private int currentPickerSlot;
    private CachedDriver driver1;
    private CachedDriver driver2;

    private ImageView ivHeadshot1, ivHeadshot2;
    private TextView tvDriver1Name, tvDriver1Team;
    private TextView tvDriver2Name, tvDriver2Team;
    private LinearLayout sectionStats, sectionH2h, llStatsRows;
    private ProgressBar pbLoading;
    private TextView tvH2hD1, tvH2hD2;
    private View viewH2hD1Bar, viewH2hD2Bar;

    private StatRowHolder rowPoints, rowWins, rowPodiums, rowDnfs;
    private StatRowHolder rowAvgPos, rowBestGrid, rowPoles;

    private final Gson gson = new Gson();

    private static class DriverStats {
        double points = 0;
        int wins = 0, podiums = 0, dnfs = 0, poles = 0;
        int finishCount = 0, finishPositionTotal = 0;
        int bestGrid = Integer.MAX_VALUE;
        int h2hWins = 0;

        double avgFinishPos() {
            return finishCount > 0 ? (double) finishPositionTotal / finishCount : 0;
        }
    }

    private static class StatRowHolder {
        final View root;
        final TextView tvLabel, tvD1Value, tvD2Value;
        final View viewD1Bar, viewD2Bar;

        StatRowHolder(View root) {
            this.root = root;
            tvLabel   = root.findViewById(R.id.tv_stat_label);
            tvD1Value = root.findViewById(R.id.tv_d1_value);
            tvD2Value = root.findViewById(R.id.tv_d2_value);
            viewD1Bar = root.findViewById(R.id.view_d1_bar);
            viewD2Bar = root.findViewById(R.id.view_d2_bar);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare_drivers);

        year = getIntent().getIntExtra(EXTRA_YEAR, SeasonHelper.getCurrentYear());

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Compare Drivers");
        }

        ivHeadshot1   = findViewById(R.id.iv_headshot1);
        ivHeadshot2   = findViewById(R.id.iv_headshot2);
        tvDriver1Name = findViewById(R.id.tv_driver1_name);
        tvDriver1Team = findViewById(R.id.tv_driver1_team);
        tvDriver2Name = findViewById(R.id.tv_driver2_name);
        tvDriver2Team = findViewById(R.id.tv_driver2_team);
        sectionStats  = findViewById(R.id.section_stats);
        sectionH2h    = findViewById(R.id.section_h2h);
        llStatsRows   = findViewById(R.id.ll_stats_rows);
        pbLoading     = findViewById(R.id.pb_loading);
        tvH2hD1       = findViewById(R.id.tv_h2h_d1);
        tvH2hD2       = findViewById(R.id.tv_h2h_d2);
        viewH2hD1Bar  = findViewById(R.id.view_h2h_d1_bar);
        viewH2hD2Bar  = findViewById(R.id.view_h2h_d2_bar);

        rowPoints   = inflateStatRow("Points");
        rowWins     = inflateStatRow("Wins");
        rowPodiums  = inflateStatRow("Podiums");
        rowDnfs     = inflateStatRow("DNFs");
        rowAvgPos   = inflateStatRow("Avg Finish");
        rowBestGrid = inflateStatRow("Best Grid");
        rowPoles    = inflateStatRow("Poles");

        setupSeasonSpinner();

        findViewById(R.id.btn_select_driver1).setOnClickListener(v -> openDriverPicker(1));
        findViewById(R.id.btn_select_driver2).setOnClickListener(v -> openDriverPicker(2));
    }

    private void setupSeasonSpinner() {
        MaterialAutoCompleteTextView spinner = findViewById(R.id.spinner_season);
        List<String> seasons = SeasonHelper.getAllSeasons();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, seasons) {
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence c) {
                        FilterResults r = new FilterResults();
                        r.values = seasons;
                        r.count  = seasons.size();
                        return r;
                    }
                    @Override
                    protected void publishResults(CharSequence c, FilterResults r) {
                        notifyDataSetChanged();
                    }
                };
            }
        };

        spinner.setAdapter(adapter);
        spinner.setText(String.valueOf(year), false);
        spinner.setDropDownHeight(600);
        spinner.setOnItemClickListener((parent, v, position, id) -> {
            year = Integer.parseInt(seasons.get(position));
            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle(year + " Season");
            }
            resetDriverSelection();
        });
    }

    private void resetDriverSelection() {
        driver1 = null;
        driver2 = null;
        resetDriverCard(ivHeadshot1, tvDriver1Name, tvDriver1Team);
        resetDriverCard(ivHeadshot2, tvDriver2Name, tvDriver2Team);
        sectionStats.setVisibility(View.GONE);
        sectionH2h.setVisibility(View.GONE);
        pbLoading.setVisibility(View.GONE);
    }

    private void resetDriverCard(ImageView iv, TextView tvName, TextView tvTeam) {
        Glide.with(this).clear(iv);
        iv.setImageDrawable(null);
        iv.setBackgroundResource(R.drawable.bg_headshot_placeholder);
        tvName.setText("Tap to select");
        tvName.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvTeam.setVisibility(View.GONE);
    }

    private void openDriverPicker(int slot) {
        currentPickerSlot = slot;
        DriverPickerBottomSheet sheet = DriverPickerBottomSheet.newInstance(year);
        sheet.show(getSupportFragmentManager(), "driver_picker");
    }

    @Override
    public void onDriverSelected(CachedDriver driver) {
        if (currentPickerSlot == 1) {
            driver1 = driver;
            updateDriverCard(ivHeadshot1, tvDriver1Name, tvDriver1Team, driver);
        } else {
            driver2 = driver;
            updateDriverCard(ivHeadshot2, tvDriver2Name, tvDriver2Team, driver);
        }
        if (driver1 != null && driver2 != null) {
            computeAndShowStats();
        }
    }

    private void updateDriverCard(ImageView iv, TextView tvName, TextView tvTeam,
                                   CachedDriver driver) {
        String first = driver.firstName != null ? driver.firstName : "";
        String last  = driver.lastName  != null ? driver.lastName  : "";
        tvName.setText((first + " " + last).trim());
        tvName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

        if (driver.teamName != null) {
            tvTeam.setText(driver.teamName);
            try {
                tvTeam.setTextColor(driver.teamColour != null
                        ? Color.parseColor(driver.teamColour) : Color.WHITE);
            } catch (Exception e) {
                tvTeam.setTextColor(Color.WHITE);
            }
            tvTeam.setVisibility(View.VISIBLE);
        }

        if (driver.headshotUrl != null && !driver.headshotUrl.isEmpty()) {
            Glide.with(this)
                    .load(driver.headshotUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.bg_headshot_placeholder)
                    .into(iv);
        } else {
            Glide.with(this).clear(iv);
            iv.setImageDrawable(null);
            iv.setBackgroundResource(R.drawable.bg_headshot_placeholder);
        }
    }

    private void computeAndShowStats() {
        pbLoading.setVisibility(View.VISIBLE);
        sectionStats.setVisibility(View.GONE);
        sectionH2h.setVisibility(View.GONE);

        String driverId1 = driver1.driverId;
        String driverId2 = driver2.driverId;

        F1Repository repo = new F1Repository(
                F1App.get().getDatabase(),
                F1ApiClient.getInstance(F1App.get()).getService());

        repo.ensureSeasonResultsCached(year, new F1Repository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                computeStatsFromRoom(driverId1, driverId2);
            }
            @Override
            public void onError(String error) {
                computeStatsFromRoom(driverId1, driverId2);
            }
        });
    }

    private void computeStatsFromRoom(String driverId1, String driverId2) {
        new Thread(() -> {
            List<CachedResult> allResults = F1App.get().getDatabase().resultDao().getByYear(year);


            DriverStats stats1 = new DriverStats();
            DriverStats stats2 = new DriverStats();

            Type mapType  = new TypeToken<Map<String, Object>>(){}.getType();
            Type listType = new TypeToken<List<RaceResult>>(){}.getType();

            for (CachedResult cached : allResults) {
                if (!"Race".equals(cached.sessionType)) continue;
                if (cached.resultsJson == null) continue;

                Map<String, Object> body;
                try {
                    body = gson.fromJson(cached.resultsJson, mapType);
                } catch (Exception e) { continue; }

                Object resultsObj = body.get("results");
                if (!(resultsObj instanceof List)) continue;

                List<RaceResult> results;
                try {
                    results = gson.fromJson(gson.toJson(resultsObj), listType);
                } catch (Exception e) { continue; }
                if (results == null) continue;

                int roundPos1 = -1, roundPos2 = -1;

                for (RaceResult r : results) {
                    if (r.getDriver() == null) continue;
                    String dId = r.getDriver().getDriverId();
                    boolean isD1 = driverId1.equals(dId);
                    boolean isD2 = driverId2.equals(dId);
                    if (!isD1 && !isD2) continue;

                    DriverStats stats = isD1 ? stats1 : stats2;
                    String status = r.getStatus();
                    boolean dnf   = isDnf(status);
                    double pts    = parseDouble(r.getPoints());
                    int posInt    = parseInt(r.getPosition());
                    int grid      = parseInt(r.getGridPosition());

                    stats.points += pts;
                    if (dnf) {
                        stats.dnfs++;
                    } else {
                        if (posInt == 1) stats.wins++;
                        if (posInt <= 3 && posInt > 0) stats.podiums++;
                        if (posInt > 0) {
                            stats.finishCount++;
                            stats.finishPositionTotal += posInt;
                        }
                    }
                    if (grid > 0 && grid < stats.bestGrid) stats.bestGrid = grid;
                    if (grid == 1) stats.poles++;

                    if (!dnf && posInt > 0) {
                        if (isD1) roundPos1 = posInt;
                        else       roundPos2 = posInt;
                    }
                }

                if (roundPos1 > 0 && roundPos2 > 0) {
                    if (roundPos1 < roundPos2) stats1.h2hWins++;
                    else                        stats2.h2hWins++;
                }
            }

            final DriverStats fStats1 = stats1;
            final DriverStats fStats2 = stats2;

            if (isFinishing() || isDestroyed()) return;
            runOnUiThread(() -> {
                pbLoading.setVisibility(View.GONE);
                displayStats(fStats1, fStats2);
            });
        }).start();
    }

    private void displayStats(DriverStats s1, DriverStats s2) {
        int color1 = safeParseColor(driver1.teamColour, "#FFFFFF");
        int color2 = safeParseColor(driver2.teamColour, "#FFFFFF");

        updateStatRow(rowPoints,   s1.points,         s2.points,         color1, color2, false,
                formatPoints(s1.points),        formatPoints(s2.points));
        updateStatRow(rowWins,     s1.wins,           s2.wins,           color1, color2, false,
                String.valueOf(s1.wins),        String.valueOf(s2.wins));
        updateStatRow(rowPodiums,  s1.podiums,        s2.podiums,        color1, color2, false,
                String.valueOf(s1.podiums),     String.valueOf(s2.podiums));
        updateStatRow(rowDnfs,     s1.dnfs,           s2.dnfs,           color1, color2, false,
                String.valueOf(s1.dnfs),        String.valueOf(s2.dnfs));

        double avg1 = s1.avgFinishPos();
        double avg2 = s2.avgFinishPos();
        String avgLabel1 = avg1 > 0 ? String.format("%.1f", avg1) : "--";
        String avgLabel2 = avg2 > 0 ? String.format("%.1f", avg2) : "--";
        updateStatRow(rowAvgPos, avg1, avg2, color1, color2, true, avgLabel1, avgLabel2);

        int grid1 = s1.bestGrid == Integer.MAX_VALUE ? 0 : s1.bestGrid;
        int grid2 = s2.bestGrid == Integer.MAX_VALUE ? 0 : s2.bestGrid;
        String gridLabel1 = grid1 > 0 ? "P" + grid1 : "--";
        String gridLabel2 = grid2 > 0 ? "P" + grid2 : "--";
        updateStatRow(rowBestGrid, grid1, grid2, color1, color2, true, gridLabel1, gridLabel2);

        updateStatRow(rowPoles, s1.poles, s2.poles, color1, color2, false,
                String.valueOf(s1.poles), String.valueOf(s2.poles));

        // H2H section
        tvH2hD1.setText(String.valueOf(s1.h2hWins));
        tvH2hD2.setText(String.valueOf(s2.h2hWins));
        float total = s1.h2hWins + s2.h2hWins;
        float w1 = total == 0 ? 1f : s1.h2hWins / total;
        float w2 = total == 0 ? 1f : s2.h2hWins / total;
        setBarWeight(viewH2hD1Bar, w1, color1);
        setBarWeight(viewH2hD2Bar, w2, color2);

        sectionStats.setVisibility(View.VISIBLE);
        sectionH2h.setVisibility(View.VISIBLE);
    }

    private void updateStatRow(StatRowHolder row, double val1, double val2,
                                int color1, int color2, boolean lowerIsBetter,
                                String label1, String label2) {
        row.tvD1Value.setText(label1);
        row.tvD2Value.setText(label2);

        float w1, w2;
        double total = val1 + val2;
        if (total == 0) {
            w1 = w2 = 1f;
        } else if (lowerIsBetter) {
            // lower val = bigger bar (inverted)
            w1 = val1 == 0 && val2 == 0 ? 1f : (float)(val2 / total);
            w2 = val1 == 0 && val2 == 0 ? 1f : (float)(val1 / total);
        } else {
            w1 = (float)(val1 / total);
            w2 = (float)(val2 / total);
        }

        setBarWeight(row.viewD1Bar, w1, color1);
        setBarWeight(row.viewD2Bar, w2, color2);
    }

    private void setBarWeight(View bar, float weight, int color) {
        bar.setBackgroundColor(color);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bar.getLayoutParams();
        params.weight = weight;
        bar.setLayoutParams(params);
    }

    private StatRowHolder inflateStatRow(String label) {
        LayoutInflater inflater = getLayoutInflater();
        View row = inflater.inflate(R.layout.item_stat_compare_row, llStatsRows, false);
        StatRowHolder holder = new StatRowHolder(row);
        holder.tvLabel.setText(label);
        llStatsRows.addView(row);

        View divider = new View(this);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(dp);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_divider));
        llStatsRows.addView(divider);

        return holder;
    }

    private static boolean isDnf(String status) {
        if (status == null) return false;
        String s = status.toLowerCase();
        return s.equals("dnf") || s.equals("dsq") || s.equals("dns")
                || s.equals("retired") || s.equals("accident") || s.equals("collision")
                || (!s.equals("finished") && !s.startsWith("+") && !s.matches("\\d+.*"));
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private static String formatPoints(double pts) {
        if (pts == (int) pts) return String.valueOf((int) pts);
        return String.valueOf(pts);
    }

    private static int safeParseColor(String colour, String fallback) {
        try {
            return Color.parseColor(colour != null ? colour : fallback);
        } catch (Exception e) {
            try { return Color.parseColor(fallback); } catch (Exception ignored) {}
            return Color.WHITE;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
