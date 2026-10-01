package com.f1stats;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.f1stats.util.DebugLog;
import com.f1stats.data.F1Repository;
import com.f1stats.db.CachedDriver;
import com.f1stats.db.CachedResult;
import com.f1stats.ui.compare.DriverPickerBottomSheet;
import com.f1stats.util.HeadToHead;
import com.f1stats.util.TeamColors;
import com.f1stats.util.SystemBarInsets;
import com.google.gson.Gson;

import java.util.List;

public class CompareDriversActivity extends AppCompatActivity
        implements DriverPickerBottomSheet.OnDriverSelectedListener {

    public static final String EXTRA_YEAR = "extra_year";
    /** Optional Jolpica driverIds to open with a pair preselected (with EXTRA_YEAR). */
    public static final String EXTRA_DRIVER_ID_1 = "extra_driver_id_1";
    public static final String EXTRA_DRIVER_ID_2 = "extra_driver_id_2";

    private int year;
    private int currentPickerSlot;
    /** Preselection from the intent, applied once the season's drivers load. */
    private String pendingDriverId1, pendingDriverId2;
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

    private static class StatRowHolder {
        final View root;
        final TextView tvLabel, tvD1Value, tvD2Value, tvCaption;
        final View viewD1Bar, viewD2Bar;

        StatRowHolder(View root) {
            this.root = root;
            tvLabel   = root.findViewById(R.id.tv_stat_label);
            tvD1Value = root.findViewById(R.id.tv_d1_value);
            tvD2Value = root.findViewById(R.id.tv_d2_value);
            tvCaption = root.findViewById(R.id.tv_stat_caption);
            viewD1Bar = root.findViewById(R.id.view_d1_bar);
            viewD2Bar = root.findViewById(R.id.view_d2_bar);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare_drivers);
        SystemBarInsets.applyToContentRoot(this);

        year = getIntent().getIntExtra(EXTRA_YEAR, SeasonHelper.getCurrentYear());
        pendingDriverId1 = getIntent().getStringExtra(EXTRA_DRIVER_ID_1);
        pendingDriverId2 = getIntent().getStringExtra(EXTRA_DRIVER_ID_2);

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
        rowPoints.tvCaption.setText("Includes sprint points");
        rowPoints.tvCaption.setVisibility(View.VISIBLE);
        rowWins     = inflateStatRow("Wins");
        rowPodiums  = inflateStatRow("Podiums");
        rowDnfs     = inflateStatRow("DNFs");
        rowAvgPos   = inflateStatRow("Avg Finish");
        rowBestGrid = inflateStatRow("Best Grid");
        rowPoles    = inflateStatRow("Poles");

        setupSeasonSpinner();
        prefetchDriversForYear(year);

        findViewById(R.id.btn_select_driver1).setOnClickListener(v -> openDriverPicker(1));
        findViewById(R.id.btn_select_driver2).setOnClickListener(v -> openDriverPicker(2));
    }

    private void prefetchDriversForYear(int year) {
        DebugLog.d("H2H_DEBUG", "prefetchDriversForYear: year=" + year);
        pbLoading.setVisibility(View.VISIBLE);
        setDriverSelectionsEnabled(false);

        F1Repository repo = F1Repository.getInstance(F1App.get());

        repo.fetchDriversForSeason(year, new F1Repository.RepositoryCallback<List<CachedDriver>>() {
            @Override
            public void onSuccess(List<CachedDriver> drivers) {
                DebugLog.d("H2H_DEBUG", "fetchDriversForSeason onSuccess: " + drivers.size() + " drivers");
                for (int i = 0; i < Math.min(drivers.size(), 5); i++) {
                    CachedDriver d = drivers.get(i);
                    DebugLog.d("H2H_DEBUG", "  sample driver[" + i + "]: driverId=" + d.driverId + " code=" + d.code + " name=" + d.firstName + " " + d.lastName);
                }
                pbLoading.setVisibility(View.GONE);
                setDriverSelectionsEnabled(true);
                applyPreselection(drivers);
            }
            @Override
            public void onError(String error) {
                DebugLog.d("H2H_DEBUG", "fetchDriversForSeason onError: " + error);
                pbLoading.setVisibility(View.GONE);
                setDriverSelectionsEnabled(true);
            }
        });
    }

    private void applyPreselection(List<CachedDriver> drivers) {
        String id1 = pendingDriverId1;
        String id2 = pendingDriverId2;
        pendingDriverId1 = null;
        pendingDriverId2 = null;
        if (id1 == null && id2 == null) return;
        CachedDriver d1 = null, d2 = null;
        for (CachedDriver d : drivers) {
            if (id1 != null && id1.equals(d.driverId)) d1 = d;
            if (id2 != null && id2.equals(d.driverId)) d2 = d;
        }
        if (d1 != null) {
            currentPickerSlot = 1;
            onDriverSelected(d1);
        }
        if (d2 != null) {
            currentPickerSlot = 2;
            onDriverSelected(d2);
        }
    }

    private void setDriverSelectionsEnabled(boolean enabled) {
        View btn1 = findViewById(R.id.btn_select_driver1);
        View btn2 = findViewById(R.id.btn_select_driver2);
        if (btn1 != null) btn1.setEnabled(enabled);
        if (btn2 != null) btn2.setEnabled(enabled);
    }

    private void setupSeasonSpinner() {
        TextView tvYear = findViewById(R.id.tv_selected_year);
        ImageButton btnPrev = findViewById(R.id.btn_prev_year);
        ImageButton btnNext = findViewById(R.id.btn_next_year);

        tvYear.setText(String.valueOf(year));

        tvYear.setOnClickListener(v -> SeasonPickerHelper.showPicker(this, year, newYear -> {
            year = newYear;
            tvYear.setText(String.valueOf(year));
            if (getSupportActionBar() != null) getSupportActionBar().setSubtitle(year + " Season");
            resetDriverSelection();
            prefetchDriversForYear(year);
        }));

        btnPrev.setOnClickListener(v -> {
            if (year > 1950) {
                year--;
                tvYear.setText(String.valueOf(year));
                if (getSupportActionBar() != null) getSupportActionBar().setSubtitle(year + " Season");
                resetDriverSelection();
                prefetchDriversForYear(year);
            }
        });
        btnNext.setOnClickListener(v -> {
            if (year < SeasonHelper.getCurrentYear()) {
                year++;
                tvYear.setText(String.valueOf(year));
                if (getSupportActionBar() != null) getSupportActionBar().setSubtitle(year + " Season");
                resetDriverSelection();
                prefetchDriversForYear(year);
            }
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
        DebugLog.d("H2H_DEBUG", "onDriverSelected: slot=" + currentPickerSlot + " driverId=" + driver.driverId + " code=" + driver.code + " name=" + driver.firstName + " " + driver.lastName);
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
            tvTeam.setTextColor(TeamColors.get(this, null, driver.teamName, driver.teamColour));
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
        HeadToHead.DriverRef key1 = refFor(driver1);
        HeadToHead.DriverRef key2 = refFor(driver2);
        DebugLog.d("H2H_DEBUG", "computeAndShowStats: year=" + year + " d1=" + key1 + " d2=" + key2);
        pbLoading.setVisibility(View.VISIBLE);
        sectionStats.setVisibility(View.GONE);
        sectionH2h.setVisibility(View.GONE);

        F1Repository repo = F1Repository.getInstance(F1App.get());

        repo.ensureSeasonResultsCached(year, new F1Repository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                DebugLog.d("H2H_DEBUG", "ensureSeasonResultsCached onSuccess — calling computeStatsFromRoom");
                computeStatsFromRoom(key1, key2);
            }
            @Override
            public void onError(String error) {
                DebugLog.d("H2H_DEBUG", "ensureSeasonResultsCached onError: " + error + " — still calling computeStatsFromRoom");
                computeStatsFromRoom(key1, key2);
            }
        });
    }

    /** Matches results by Jolpica driverId; code only when the picked driver has no driverId. */
    private static HeadToHead.DriverRef refFor(CachedDriver d) {
        return new HeadToHead.DriverRef(d.driverId, d.code);
    }

    private void computeStatsFromRoom(HeadToHead.DriverRef key1, HeadToHead.DriverRef key2) {
        final int statsYear = year;
        new Thread(() -> {
            List<CachedResult> allResults = F1App.get().getDatabase().resultDao().getByYear(statsYear);
            DebugLog.d("H2H_DEBUG", "computeStatsFromRoom: year=" + statsYear + " totalRows=" + allResults.size()
                    + " d1=" + key1 + " d2=" + key2);

            HeadToHead.Season season = new HeadToHead.Season(statsYear);
            for (CachedResult cached : allResults) {
                season.addResultsJson(cached.round, cached.sessionType, cached.resultsJson, gson);
            }
            HeadToHead.Comparison comparison = HeadToHead.compare(season, key1, key2);

            DebugLog.d("H2H_DEBUG", "FINAL stats1: pts=" + comparison.driver1.points + " wins=" + comparison.driver1.wins
                    + " podiums=" + comparison.driver1.podiums + " dnfs=" + comparison.driver1.dnfs + " h2h=" + comparison.driver1.h2hWins);
            DebugLog.d("H2H_DEBUG", "FINAL stats2: pts=" + comparison.driver2.points + " wins=" + comparison.driver2.wins
                    + " podiums=" + comparison.driver2.podiums + " dnfs=" + comparison.driver2.dnfs + " h2h=" + comparison.driver2.h2hWins);

            if (isFinishing() || isDestroyed()) return;
            runOnUiThread(() -> {
                pbLoading.setVisibility(View.GONE);
                displayStats(comparison.driver1, comparison.driver2);
            });
        }).start();
    }

    private void displayStats(HeadToHead.DriverStats s1, HeadToHead.DriverStats s2) {
        int color1 = TeamColors.get(this, null, driver1.teamName, driver1.teamColour);
        int color2 = TeamColors.get(this, null, driver2.teamName, driver2.teamColour);

        updateStatRow(rowPoints,   s1.points,         s2.points,         color1, color2, false,
                formatPoints(s1.points),        formatPoints(s2.points));
        updateStatRow(rowWins,     s1.wins,           s2.wins,           color1, color2, false,
                String.valueOf(s1.wins),        String.valueOf(s2.wins));
        updateStatRow(rowPodiums,  s1.podiums,        s2.podiums,        color1, color2, false,
                String.valueOf(s1.podiums),     String.valueOf(s2.podiums));
        updateStatRow(rowDnfs,     s1.dnfs,           s2.dnfs,           color1, color2, false,
                String.valueOf(s1.dnfs),        String.valueOf(s2.dnfs));

        double avg1 = s1.avgFinish();
        double avg2 = s2.avgFinish();
        String avgLabel1 = avg1 > 0 ? String.format("%.1f", avg1) : "--";
        String avgLabel2 = avg2 > 0 ? String.format("%.1f", avg2) : "--";
        updateStatRow(rowAvgPos, avg1, avg2, color1, color2, true, avgLabel1, avgLabel2);

        int grid1 = s1.bestGrid;
        int grid2 = s2.bestGrid;
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

    private static String formatPoints(double pts) {
        if (pts == (int) pts) return String.valueOf((int) pts);
        return String.valueOf(pts);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
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
