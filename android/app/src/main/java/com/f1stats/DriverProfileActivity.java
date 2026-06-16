package com.f1stats;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.f1stats.api.F1ApiClient;
import com.f1stats.data.F1Repository;
import com.f1stats.db.CachedDriver;
import com.f1stats.models.RaceResult;
import com.f1stats.ui.driver.DriverResultAdapter;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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

    private ShapeableImageView ivHeadshot;
    private TextView tvDriverName, tvNumberFlag, tvTeamName, tvDob;
    private TextView tvNumberWatermark;
    private View viewHeaderGradient;
    private TextView tvStatPoints, tvStatWins, tvStatPodiums;
    private TextView tvStatDnfs, tvStatBestGrid, tvStatPoles;
    private DriverResultAdapter resultsAdapter;
    private ProgressBar pbLoading;
    private String driverCode;

    private final Gson gson = new Gson();
    private static final int BATCH_SIZE = 3;

    private static class RoundSession {
        final int round;
        final String sessionType;
        final String raceName;

        RoundSession(int round, String sessionType, String raceName) {
            this.round = round;
            this.sessionType = sessionType;
            this.raceName = raceName;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_profile);

        String driverId    = getIntent().getStringExtra(EXTRA_DRIVER_ID);
        driverCode         = getIntent().getStringExtra(EXTRA_DRIVER_CODE);
        String driverName  = getIntent().getStringExtra(EXTRA_DRIVER_NAME);
        int year           = getIntent().getIntExtra(EXTRA_YEAR, 2026);
        String headshotUrl = getIntent().getStringExtra(EXTRA_HEADSHOT_URL);
        String teamName    = getIntent().getStringExtra(EXTRA_TEAM_NAME);
        String teamColour  = getIntent().getStringExtra(EXTRA_TEAM_COLOUR);
        String nationality = getIntent().getStringExtra(EXTRA_NATIONALITY);
        String dob         = getIntent().getStringExtra(EXTRA_DOB);
        String number      = getIntent().getStringExtra(EXTRA_NUMBER);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(driverName != null ? driverName : "Driver");
            getSupportActionBar().setSubtitle(year + " Season");
        }

        ivHeadshot         = findViewById(R.id.iv_headshot);
        tvDriverName       = findViewById(R.id.tv_driver_name);
        tvNumberFlag       = findViewById(R.id.tv_number_flag);
        tvTeamName         = findViewById(R.id.tv_team_name);
        tvDob              = findViewById(R.id.tv_dob);
        tvNumberWatermark  = findViewById(R.id.tv_number_watermark);
        viewHeaderGradient = findViewById(R.id.view_header_gradient);
        tvStatPoints       = findViewById(R.id.tv_stat_points);
        tvStatWins         = findViewById(R.id.tv_stat_wins);
        tvStatPodiums      = findViewById(R.id.tv_stat_podiums);
        tvStatDnfs         = findViewById(R.id.tv_stat_dnfs);
        tvStatBestGrid     = findViewById(R.id.tv_stat_best_grid);
        tvStatPoles        = findViewById(R.id.tv_stat_poles);
        pbLoading          = findViewById(R.id.pb_loading);

        RecyclerView rv = findViewById(R.id.rv_driver_results);
        rv.setLayoutManager(new LinearLayoutManager(this));
        resultsAdapter = new DriverResultAdapter();
        rv.setAdapter(resultsAdapter);

        populateHeader(driverName, number, nationality, teamName, teamColour, dob, headshotUrl);
        loadStats(driverId, year, headshotUrl);
    }

    private void populateHeader(String name, String number, String nationality,
                                String teamName, String teamColour, String dob,
                                String headshotUrl) {
        tvDriverName.setText(name != null ? name : "");

        tvNumberWatermark.setText(number != null ? number : "");

        String flag = DriverHelper.getFlag(nationality);
        String numDisplay = number != null && !number.isEmpty() ? "#" + number : "";
        String natShort = DriverHelper.getShortNationality(nationality);
        tvNumberFlag.setText(flag + " " + natShort +
                (numDisplay.isEmpty() ? "" : "  ·  " + numDisplay));

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

        // Team colour border on headshot
        int teamColor;
        try {
            teamColor = teamColour != null ? Color.parseColor(teamColour) : Color.WHITE;
        } catch (Exception e) {
            teamColor = Color.WHITE;
        }
        ivHeadshot.setStrokeColor(ColorStateList.valueOf(teamColor));

        // Team colour gradient on header background
        int alpha26 = Color.argb(26, Color.red(teamColor), Color.green(teamColor), Color.blue(teamColor));
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{alpha26, Color.TRANSPARENT});
        viewHeaderGradient.setBackground(gradient);

        if (headshotUrl != null && !headshotUrl.isEmpty()) {
            Glide.with(this)
                    .load(headshotUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(ivHeadshot);
        }
    }

    private void loadStats(String driverId, int year, String existingHeadshotUrl) {
        if (driverId == null) return;

        pbLoading.setVisibility(View.VISIBLE);

        F1Repository repo = new F1Repository(
                F1App.get().getDatabase(),
                F1ApiClient.getInstance(F1App.get()).getService()
        );

        repo.getSchedule(year, new F1Repository.RepositoryCallback<List<Map<String, Object>>>() {
            @Override
            public void onSuccess(List<Map<String, Object>> races) {
                List<RoundSession> sessions = new ArrayList<>();
                for (Map<String, Object> race : races) {
                    int round = toInt(race.get("round"));
                    if (round == 0) continue;
                    String raceName = toStr(race.get("race_name"));
                    sessions.add(new RoundSession(round, "Race", raceName));

                    Object sessObj = race.get("sessions");
                    if (sessObj instanceof List) {
                        for (Object s : (List<?>) sessObj) {
                            if (s instanceof Map && "Sprint".equals(((Map<?, ?>) s).get("name"))) {
                                sessions.add(new RoundSession(round, "Sprint", raceName));
                                break;
                            }
                        }
                    }
                }
                fetchBatch(repo, driverId, year, sessions, 0, new ArrayList<>(), existingHeadshotUrl);
            }

            @Override
            public void onError(String error) {
                pbLoading.setVisibility(View.GONE);
            }
        });
    }

    private void fetchBatch(F1Repository repo, String driverId, int year,
                             List<RoundSession> sessions, int startIdx,
                             List<Object[]> collected, String existingHeadshotUrl) {
        if (startIdx >= sessions.size()) {
            computeAndShowStats(driverId, year, collected, existingHeadshotUrl);
            return;
        }

        int endIdx = Math.min(startIdx + BATCH_SIZE, sessions.size());
        AtomicInteger pending = new AtomicInteger(endIdx - startIdx);

        for (int i = startIdx; i < endIdx; i++) {
            RoundSession rs = sessions.get(i);
            repo.getResults(year, rs.round, rs.sessionType,
                    new F1Repository.RepositoryCallback<Map<String, Object>>() {
                        @Override
                        public void onSuccess(Map<String, Object> data) {
                            collected.add(new Object[]{rs, data});
                            if (pending.decrementAndGet() == 0) {
                                fetchBatch(repo, driverId, year, sessions, endIdx,
                                        collected, existingHeadshotUrl);
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (pending.decrementAndGet() == 0) {
                                fetchBatch(repo, driverId, year, sessions, endIdx,
                                        collected, existingHeadshotUrl);
                            }
                        }
                    });
        }
    }

    private void computeAndShowStats(String driverId, int year,
                                      List<Object[]> collected, String existingHeadshotUrl) {
        Executors.newSingleThreadExecutor().execute(() -> {
            CachedDriver cachedDriver = F1App.get().getDatabase().driverDao().get(driverId, year);
            if (cachedDriver == null && driverCode != null) {
                cachedDriver = F1App.get().getDatabase().driverDao().getByCode(driverCode, year);
            }

            Type listType = new TypeToken<List<RaceResult>>() {}.getType();
            double totalPoints = 0;
            int wins = 0, podiums = 0, dnfs = 0, poles = 0;
            int bestGrid = Integer.MAX_VALUE;
            List<DriverResultAdapter.DriverRaceResult> raceResults = new ArrayList<>();

            for (Object[] entry : collected) {
                RoundSession rs = (RoundSession) entry[0];
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) entry[1];

                Object resultsObj = body.get("results");
                if (!(resultsObj instanceof List)) continue;

                List<RaceResult> results;
                try {
                    results = gson.fromJson(gson.toJson(resultsObj), listType);
                } catch (Exception e) {
                    continue;
                }
                if (results == null) continue;

                for (RaceResult r : results) {
                    if (r.getDriver() == null) continue;
                    if (!driverId.equals(r.getDriver().getDriverId())) continue;

                    String pos = r.getPosition();
                    String status = r.getStatus();
                    boolean dnf = isDnf(status);
                    double pts = parseDouble(r.getPoints());

                    totalPoints += pts;

                    int posInt = parseInt(pos);
                    if (!dnf && "Race".equals(rs.sessionType)) {
                        if (posInt == 1) wins++;
                        if (posInt <= 3) podiums++;
                    }
                    if (dnf) dnfs++;

                    int grid = parseInt(r.getGridPosition());
                    if (grid > 0 && grid < bestGrid) bestGrid = grid;
                    if (grid == 1) poles++;

                    raceResults.add(new DriverResultAdapter.DriverRaceResult(
                            rs.round, rs.raceName, rs.sessionType, pos, r.getPoints(),
                            dnf, r.hasFastestLap()
                    ));
                    break;
                }
            }

            raceResults.sort((a, b) -> {
                int cmp = Integer.compare(a.round, b.round);
                if (cmp != 0) return cmp;
                return "Sprint".equals(a.sessionType) ? 1 : -1;
            });

            final double fPoints = totalPoints;
            final int fWins = wins, fPodiums = podiums, fDnfs = dnfs, fPoles = poles;
            final int fBestGrid = bestGrid == Integer.MAX_VALUE ? 0 : bestGrid;
            final List<DriverResultAdapter.DriverRaceResult> fResults = raceResults;
            final CachedDriver fDriver = cachedDriver;
            final boolean needsHeadshotFetch = (fDriver == null || fDriver.headshotUrl == null
                    || fDriver.headshotUrl.isEmpty()) && driverCode != null
                    && (existingHeadshotUrl == null || existingHeadshotUrl.isEmpty());

            if (isFinishing() || isDestroyed()) return;
            runOnUiThread(() -> {
                pbLoading.setVisibility(View.GONE);
                tvStatPoints.setText(formatPoints(fPoints));
                tvStatWins.setText(String.valueOf(fWins));
                tvStatPodiums.setText(String.valueOf(fPodiums));
                tvStatDnfs.setText(String.valueOf(fDnfs));
                tvStatBestGrid.setText(fBestGrid > 0 ? "P" + fBestGrid : "--");
                tvStatPoles.setText(String.valueOf(fPoles));
                resultsAdapter.setResults(fResults);

                if (fDriver != null) {
                    if ((existingHeadshotUrl == null || existingHeadshotUrl.isEmpty())
                            && fDriver.headshotUrl != null && !fDriver.headshotUrl.isEmpty()) {
                        Glide.with(DriverProfileActivity.this)
                                .load(fDriver.headshotUrl)
                                .apply(RequestOptions.circleCropTransform())
                                .into(ivHeadshot);
                    }
                    if (fDriver.dateOfBirth != null && !fDriver.dateOfBirth.isEmpty()) {
                        tvDob.setText("Born: " + formatDob(fDriver.dateOfBirth));
                        tvDob.setVisibility(View.VISIBLE);
                    }
                }

                if (needsHeadshotFetch) {
                    F1Repository fetchRepo = new F1Repository(
                            F1App.get().getDatabase(),
                            F1ApiClient.getInstance(F1App.get()).getService()
                    );
                    fetchRepo.fetchDrivers(year, new F1Repository.RepositoryCallback<java.util.List<CachedDriver>>() {
                        @Override
                        public void onSuccess(java.util.List<CachedDriver> drivers) {
                            for (CachedDriver d : drivers) {
                                if (driverCode.equals(d.code) && d.headshotUrl != null
                                        && !d.headshotUrl.isEmpty()) {
                                    if (!isFinishing() && !isDestroyed()) {
                                        Glide.with(DriverProfileActivity.this)
                                                .load(d.headshotUrl)
                                                .apply(RequestOptions.circleCropTransform())
                                                .into(ivHeadshot);
                                    }
                                    break;
                                }
                            }
                        }
                        @Override
                        public void onError(String error) {}
                    });
                }
            });
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private int toInt(Object val) {
        if (val instanceof Double)  return ((Double) val).intValue();
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (Exception ignored) {}
        }
        return 0;
    }

    private String toStr(Object val) {
        return val != null ? val.toString() : null;
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
