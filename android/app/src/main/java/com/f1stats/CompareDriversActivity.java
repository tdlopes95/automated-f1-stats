package com.f1stats;

import android.graphics.Color;
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
import com.f1stats.models.RaceResult;
import com.f1stats.ui.compare.DriverPickerBottomSheet;
import com.f1stats.util.ResultStatus;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
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
            }
            @Override
            public void onError(String error) {
                DebugLog.d("H2H_DEBUG", "fetchDriversForSeason onError: " + error);
                pbLoading.setVisibility(View.GONE);
                setDriverSelectionsEnabled(true);
            }
        });
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
        DriverKey key1 = new DriverKey(driver1);
        DriverKey key2 = new DriverKey(driver2);
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
    private static class DriverKey {
        final String driverId;
        final String code;

        DriverKey(CachedDriver d) {
            driverId = d.driverId != null ? d.driverId : "";
            code     = d.code != null ? d.code : "";
        }

        /** Returns the key that matched ("driverId" or "code"), or null. */
        String match(RaceResult.Driver rd) {
            if (!driverId.isEmpty()) {
                return driverId.equals(rd.getDriverId()) ? "driverId" : null;
            }
            return !code.isEmpty() && code.equalsIgnoreCase(rd.getCode()) ? "code" : null;
        }

        @Override
        public String toString() {
            return "driverId=" + driverId + " code=" + code;
        }
    }

    private void computeStatsFromRoom(DriverKey key1, DriverKey key2) {
        new Thread(() -> {
            List<CachedResult> allResults = F1App.get().getDatabase().resultDao().getByYear(year);

            DebugLog.d("H2H_DEBUG", "computeStatsFromRoom: year=" + year + " totalRows=" + allResults.size()
                    + " matching by driverId (code fallback): d1=" + key1 + " d2=" + key2);

            // Log session types breakdown
            int raceCount = 0, sprintCount = 0;
            for (CachedResult r : allResults) {
                if ("Race".equals(r.sessionType)) raceCount++;
                else if ("Sprint".equals(r.sessionType)) sprintCount++;
            }
            DebugLog.d("H2H_DEBUG", "  raceRows=" + raceCount + " sprintRows=" + sprintCount
                    + " (others=" + (allResults.size() - raceCount - sprintCount) + ")");

            DriverStats stats1 = new DriverStats();
            DriverStats stats2 = new DriverStats();

            Type mapType  = new TypeToken<Map<String, Object>>(){}.getType();
            Type listType = new TypeToken<List<RaceResult>>(){}.getType();

            boolean loggedFirstEntry = false;

            for (CachedResult cached : allResults) {
                boolean isRace   = "Race".equals(cached.sessionType);
                boolean isSprint = "Sprint".equals(cached.sessionType);
                if (!isRace && !isSprint) continue;
                if (cached.resultsJson == null) {
                    DebugLog.d("H2H_DEBUG", "  round=" + cached.round + " " + cached.sessionType + " resultsJson is NULL — skipping");
                    continue;
                }

                Map<String, Object> body;
                try {
                    body = gson.fromJson(cached.resultsJson, mapType);
                } catch (Exception e) {
                    DebugLog.d("H2H_DEBUG", "  round=" + cached.round + " JSON parse failed: " + e.getMessage());
                    continue;
                }

                // Log top-level keys once so we know the JSON shape
                if (!loggedFirstEntry) {
                    DebugLog.d("H2H_DEBUG", "  FIRST BODY keys: " + body.keySet());
                    Object resultsCheck = body.get("results");
                    if (resultsCheck instanceof List) {
                        List<?> rawList = (List<?>) resultsCheck;
                        DebugLog.d("H2H_DEBUG", "  'results' array size=" + rawList.size());
                        if (!rawList.isEmpty() && rawList.get(0) instanceof Map) {
                            Map<?, ?> firstEntry = (Map<?, ?>) rawList.get(0);
                            DebugLog.d("H2H_DEBUG", "  first entry keys: " + firstEntry.keySet());
                            Object driverField = firstEntry.get("Driver");
                            if (driverField instanceof Map) {
                                DebugLog.d("H2H_DEBUG", "  Driver sub-keys: " + ((Map<?, ?>) driverField).keySet());
                                DebugLog.d("H2H_DEBUG", "  Driver values: " + driverField);
                            } else {
                                DebugLog.d("H2H_DEBUG", "  'Driver' field is: " + driverField);
                            }
                        }
                    } else {
                        DebugLog.d("H2H_DEBUG", "  'results' is not a List, it is: " + (resultsCheck == null ? "null" : resultsCheck.getClass().getSimpleName()));
                    }
                    loggedFirstEntry = true;
                }

                Object resultsObj = body.get("results");
                if (!(resultsObj instanceof List)) {
                    DebugLog.d("H2H_DEBUG", "  round=" + cached.round + " 'results' not a List — skipping");
                    continue;
                }

                List<RaceResult> results;
                try {
                    results = gson.fromJson(gson.toJson(resultsObj), listType);
                } catch (Exception e) {
                    DebugLog.d("H2H_DEBUG", "  round=" + cached.round + " RaceResult parse failed: " + e.getMessage());
                    continue;
                }
                if (results == null) {
                    DebugLog.d("H2H_DEBUG", "  round=" + cached.round + " results list is null after parse");
                    continue;
                }

                DebugLog.d("H2H_DEBUG", "  round=" + cached.round + " " + cached.sessionType + " parsed " + results.size() + " RaceResult entries");

                // Log all driverIds in this round so we can see if our target exists
                StringBuilder ids = new StringBuilder();
                for (RaceResult rr : results) {
                    if (rr.getDriver() != null) ids.append(rr.getDriver().getDriverId()).append(",");
                }
                DebugLog.d("H2H_DEBUG", "  round=" + cached.round + " driverIds=[" + ids + "]");

                RaceResult round1 = null, round2 = null;

                for (RaceResult r : results) {
                    if (r.getDriver() == null) continue;
                    String matched1 = key1.match(r.getDriver());
                    String matched2 = matched1 == null ? key2.match(r.getDriver()) : null;
                    if (matched1 == null && matched2 == null) continue;
                    boolean isD1 = matched1 != null;

                    DebugLog.d("H2H_DEBUG", "  MATCH: round=" + cached.round + " " + cached.sessionType
                            + " key=" + (isD1 ? matched1 : matched2)
                            + " driverId=" + r.getDriver().getDriverId() + " code=" + r.getDriver().getCode()
                            + " -> driver" + (isD1 ? "1" : "2") + " pos=" + r.getPosition()
                            + " pts=" + r.getPoints() + " status=" + r.getStatus());

                    DriverStats stats = isD1 ? stats1 : stats2;
                    // Points include sprints; everything else is race-only
                    stats.points += parseDouble(r.getPoints());
                    if (!isRace) continue;

                    String status = r.getStatus();
                    int posInt    = parseInt(r.getPosition());
                    int grid      = parseInt(r.getGridPosition());

                    if (posInt == 1) stats.wins++;
                    if (posInt >= 1 && posInt <= 3) stats.podiums++;
                    if (ResultStatus.isDnf(status)) stats.dnfs++;
                    if (ResultStatus.isFinished(status) && posInt > 0) {
                        stats.finishCount++;
                        stats.finishPositionTotal += posInt;
                    }
                    if (grid > 0 && grid < stats.bestGrid) stats.bestGrid = grid;
                    if (grid == 1) stats.poles++;

                    if (isD1) round1 = r;
                    else      round2 = r;
                }

                // Race H2H: every round both started; Jolpica already classifies
                // retirements behind finishers, so position decides
                if (isRace && round1 != null && round2 != null
                        && !ResultStatus.didNotStart(round1.getStatus())
                        && !ResultStatus.didNotStart(round2.getStatus())) {
                    int pos1 = parseInt(round1.getPosition());
                    int pos2 = parseInt(round2.getPosition());
                    if (pos1 > 0 && pos2 > 0) {
                        if (pos1 < pos2) stats1.h2hWins++;
                        else              stats2.h2hWins++;
                    }
                }
            }

            DebugLog.d("H2H_DEBUG", "FINAL stats1: pts=" + stats1.points + " wins=" + stats1.wins + " podiums=" + stats1.podiums + " dnfs=" + stats1.dnfs + " h2h=" + stats1.h2hWins);
            DebugLog.d("H2H_DEBUG", "FINAL stats2: pts=" + stats2.points + " wins=" + stats2.wins + " podiums=" + stats2.podiums + " dnfs=" + stats2.dnfs + " h2h=" + stats2.h2hWins);

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

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
