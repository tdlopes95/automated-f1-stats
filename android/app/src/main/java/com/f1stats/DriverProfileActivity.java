package com.f1stats;

import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.f1stats.db.AppDatabase;
import com.f1stats.db.CachedDriver;
import com.f1stats.db.CachedResult;
import com.f1stats.db.CachedSchedule;
import com.f1stats.models.RaceResult;
import com.f1stats.ui.driver.DriverResultAdapter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class DriverProfileActivity extends AppCompatActivity {

    public static final String EXTRA_DRIVER_ID    = "extra_driver_id";
    public static final String EXTRA_DRIVER_CODE  = "extra_driver_code";
    public static final String EXTRA_DRIVER_NAME  = "extra_driver_name";
    public static final String EXTRA_YEAR         = "extra_year";
    public static final String EXTRA_HEADSHOT_URL = "extra_headshot_url";
    public static final String EXTRA_TEAM_NAME    = "extra_team_name";
    public static final String EXTRA_TEAM_COLOUR  = "extra_team_colour";
    public static final String EXTRA_NATIONALITY  = "extra_nationality";
    public static final String EXTRA_DOB          = "extra_dob";
    public static final String EXTRA_NUMBER       = "extra_number";

    private ImageView ivHeadshot;
    private TextView tvDriverName, tvNumberFlag, tvTeamName, tvDob;
    private TextView tvStatPoints, tvStatWins, tvStatPodiums;
    private TextView tvStatDnfs, tvStatBestGrid, tvStatPoles;
    private DriverResultAdapter resultsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_profile);

        String driverId    = getIntent().getStringExtra(EXTRA_DRIVER_ID);
        String driverName  = getIntent().getStringExtra(EXTRA_DRIVER_NAME);
        int year           = getIntent().getIntExtra(EXTRA_YEAR, 2026);
        String headshotUrl = getIntent().getStringExtra(EXTRA_HEADSHOT_URL);
        String teamName    = getIntent().getStringExtra(EXTRA_TEAM_NAME);
        String teamColour  = getIntent().getStringExtra(EXTRA_TEAM_COLOUR);
        String nationality = getIntent().getStringExtra(EXTRA_NATIONALITY);
        String dob         = getIntent().getStringExtra(EXTRA_DOB);
        String number      = getIntent().getStringExtra(EXTRA_NUMBER);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(driverName != null ? driverName : "Driver");
            getSupportActionBar().setSubtitle(year + " Season");
        }

        // Bind views
        ivHeadshot    = findViewById(R.id.iv_headshot);
        tvDriverName  = findViewById(R.id.tv_driver_name);
        tvNumberFlag  = findViewById(R.id.tv_number_flag);
        tvTeamName    = findViewById(R.id.tv_team_name);
        tvDob         = findViewById(R.id.tv_dob);
        tvStatPoints  = findViewById(R.id.tv_stat_points);
        tvStatWins    = findViewById(R.id.tv_stat_wins);
        tvStatPodiums = findViewById(R.id.tv_stat_podiums);
        tvStatDnfs    = findViewById(R.id.tv_stat_dnfs);
        tvStatBestGrid = findViewById(R.id.tv_stat_best_grid);
        tvStatPoles   = findViewById(R.id.tv_stat_poles);

        RecyclerView rv = findViewById(R.id.rv_driver_results);
        rv.setLayoutManager(new LinearLayoutManager(this));
        resultsAdapter = new DriverResultAdapter();
        rv.setAdapter(resultsAdapter);

        // Populate header from intent extras
        populateHeader(driverName, number, nationality, teamName, teamColour, dob, headshotUrl);

        // Load data from Room in background
        loadFromRoom(driverId, year, headshotUrl);
    }

    private void populateHeader(String name, String number, String nationality,
                                String teamName, String teamColour, String dob,
                                String headshotUrl) {
        tvDriverName.setText(name != null ? name : "");

        String flag = DriverHelper.getFlag(nationality);
        String numDisplay = number != null && !number.isEmpty() ? "#" + number : "";
        String natShort = DriverHelper.getShortNationality(nationality);
        tvNumberFlag.setText(numDisplay + " · " + flag + " " + natShort);

        if (teamName != null) {
            tvTeamName.setText(teamName);
            try {
                tvTeamName.setTextColor(teamColour != null ?
                        Color.parseColor(teamColour) : Color.WHITE);
            } catch (Exception e) {
                tvTeamName.setTextColor(Color.WHITE);
            }
        }

        if (dob != null && !dob.isEmpty()) {
            tvDob.setText("Born: " + formatDob(dob));
            tvDob.setVisibility(View.VISIBLE);
        }

        if (headshotUrl != null && !headshotUrl.isEmpty()) {
            Glide.with(this)
                    .load(headshotUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(ivHeadshot);
        }
    }

    private void loadFromRoom(String driverId, int year, String existingHeadshotUrl) {
        if (driverId == null) return;

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            // Try to enrich header data from CachedDriver
            CachedDriver cachedDriver = db.driverDao().get(driverId, year);

            // Build round → race name map from schedule
            List<CachedSchedule> schedules = db.scheduleDao().getByYear(year);
            Map<Integer, String> roundNames = new HashMap<>();
            for (CachedSchedule s : schedules) roundNames.put(s.round, s.raceName);

            // Get all Race + Sprint results for the year
            List<CachedResult> allCached = db.resultDao().getByYear(year);

            Gson gson = new Gson();
            Type listType = new TypeToken<List<RaceResult>>() {}.getType();

            double totalPoints = 0;
            int wins = 0, podiums = 0, dnfs = 0, poles = 0;
            int bestGrid = Integer.MAX_VALUE;
            List<DriverResultAdapter.DriverRaceResult> raceResults = new ArrayList<>();

            for (CachedResult cached : allCached) {
                if (!"Race".equals(cached.sessionType) && !"Sprint".equals(cached.sessionType)) {
                    continue;
                }
                if (cached.resultsJson == null) continue;

                List<RaceResult> results;
                try {
                    results = gson.fromJson(cached.resultsJson, listType);
                } catch (Exception e) {
                    continue;
                }
                if (results == null) continue;

                for (RaceResult r : results) {
                    if (r.getDriver() == null) continue;
                    if (!driverId.equals(r.getDriver().getDriverId())) continue;

                    String pos = r.getPosition();
                    String status = r.getStatus();
                    boolean isDnf = isDnf(status);
                    double pts = parseDouble(r.getPoints());

                    totalPoints += pts;

                    int posInt = parseInt(pos);
                    if (!isDnf && "Race".equals(cached.sessionType)) {
                        if (posInt == 1) wins++;
                        if (posInt <= 3) podiums++;
                    }
                    if (isDnf) dnfs++;

                    // Grid position (from race entry, represents qualifying result)
                    int grid = parseInt(r.getGridPosition());
                    if (grid > 0 && grid < bestGrid) bestGrid = grid;
                    if (grid == 1) poles++;

                    String raceName = roundNames.getOrDefault(cached.round, "Round " + cached.round);
                    raceResults.add(new DriverResultAdapter.DriverRaceResult(
                            cached.round,
                            raceName,
                            cached.sessionType,
                            pos,
                            r.getPoints(),
                            isDnf
                    ));
                    break; // found this driver, move to next cached result
                }
            }

            // Sort by round ascending
            raceResults.sort((a, b) -> {
                int cmp = Integer.compare(a.round, b.round);
                if (cmp != 0) return cmp;
                // Race before Sprint within same round
                return "Sprint".equals(a.sessionType) ? 1 : -1;
            });

            final double fPoints = totalPoints;
            final int fWins = wins, fPodiums = podiums, fDnfs = dnfs;
            final int fPoles = poles;
            final int fBestGrid = bestGrid == Integer.MAX_VALUE ? 0 : bestGrid;
            final List<DriverResultAdapter.DriverRaceResult> fResults = raceResults;
            final CachedDriver fDriver = cachedDriver;

            runOnUiThread(() -> {
                // Update stats
                tvStatPoints.setText(formatPoints(fPoints));
                tvStatWins.setText(String.valueOf(fWins));
                tvStatPodiums.setText(String.valueOf(fPodiums));
                tvStatDnfs.setText(String.valueOf(fDnfs));
                tvStatBestGrid.setText(fBestGrid > 0 ? "P" + fBestGrid : "--");
                tvStatPoles.setText(String.valueOf(fPoles));

                resultsAdapter.setResults(fResults);

                // Enrich header if CachedDriver has more info
                if (fDriver != null) {
                    if ((existingHeadshotUrl == null || existingHeadshotUrl.isEmpty())
                            && fDriver.headshotUrl != null && !fDriver.headshotUrl.isEmpty()) {
                        Glide.with(this)
                                .load(fDriver.headshotUrl)
                                .apply(RequestOptions.circleCropTransform())
                                .into(ivHeadshot);
                    }
                    if (fDriver.dateOfBirth != null && !fDriver.dateOfBirth.isEmpty()) {
                        tvDob.setText("Born: " + formatDob(fDriver.dateOfBirth));
                        tvDob.setVisibility(View.VISIBLE);
                    }
                }
            });
        });
    }

    private boolean isDnf(String status) {
        if (status == null) return false;
        String s = status.toLowerCase();
        return s.equals("dnf") || s.equals("dsq") || s.equals("dns")
                || s.equals("retired") || s.equals("accident") || s.equals("collision")
                || (!s.equals("finished") && !s.startsWith("+") && !s.matches("\\d+.*"));
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private String formatPoints(double pts) {
        if (pts == (int) pts) return String.valueOf((int) pts);
        return String.valueOf(pts);
    }

    private String formatDob(String dob) {
        // dob is "YYYY-MM-DD", format to "DD Mon YYYY"
        if (dob == null || dob.length() < 10) return dob;
        try {
            String[] parts = dob.split("-");
            int month = Integer.parseInt(parts[1]);
            String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            return parts[2] + " " + months[month - 1] + " " + parts[0];
        } catch (Exception e) {
            return dob;
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
