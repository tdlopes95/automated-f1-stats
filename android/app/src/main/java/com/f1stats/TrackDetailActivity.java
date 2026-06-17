package com.f1stats;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.f1stats.db.AppDatabase;
import com.f1stats.db.CachedResult;
import com.f1stats.db.CachedSchedule;
import com.f1stats.models.QualifyingResult;
import com.f1stats.models.RaceResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrackDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CIRCUIT_IMAGE = "extra_circuit_image";
    public static final String EXTRA_CIRCUIT_NAME  = "extra_circuit_name";
    public static final String EXTRA_COUNTRY       = "extra_country";
    public static final String EXTRA_LOCALITY      = "extra_locality";
    public static final String EXTRA_COUNTRY_FLAG  = "extra_country_flag";

    private ImageView ivHero;
    private ScaleGestureDetector scaleDetector;
    private float currentScale = 1f;

    private TextView tvLastWinner, tvLastWinnerYear;
    private LinearLayout llWinsContainer, llPolesContainer;
    private TextView tvWinsEmpty, tvPolesEmpty;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_detail);

        String circuitImage = getIntent().getStringExtra(EXTRA_CIRCUIT_IMAGE);
        String circuitName  = getIntent().getStringExtra(EXTRA_CIRCUIT_NAME);
        String country      = getIntent().getStringExtra(EXTRA_COUNTRY);
        String locality     = getIntent().getStringExtra(EXTRA_LOCALITY);
        String countryFlag  = getIntent().getStringExtra(EXTRA_COUNTRY_FLAG);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(circuitName != null ? circuitName : "Track Detail");
        }

        ivHero = findViewById(R.id.iv_hero_image);
        if (circuitImage != null && !circuitImage.isEmpty()) {
            Glide.with(this).load(circuitImage).into(ivHero);
        }

        setupZoom();

        TextView tvCircuitName = findViewById(R.id.tv_circuit_name);
        tvCircuitName.setText(circuitName != null ? circuitName : "");

        TextView tvLocation = findViewById(R.id.tv_location);
        ImageView ivFlag = findViewById(R.id.iv_country_flag);
        String location = buildLocation(locality, country);
        tvLocation.setText(location);
        if (countryFlag != null && !countryFlag.isEmpty()) {
            ivFlag.setVisibility(View.VISIBLE);
            Glide.with(this).load(countryFlag).into(ivFlag);
        }

        tvLastWinner     = findViewById(R.id.tv_last_winner);
        tvLastWinnerYear = findViewById(R.id.tv_last_winner_year);
        llWinsContainer  = findViewById(R.id.ll_wins_container);
        llPolesContainer = findViewById(R.id.ll_poles_container);
        tvWinsEmpty      = findViewById(R.id.tv_wins_empty);
        tvPolesEmpty     = findViewById(R.id.tv_poles_empty);

        if (circuitName != null && !circuitName.isEmpty()) {
            loadStats(circuitName);
        }
    }

    private void setupZoom() {
        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        currentScale *= detector.getScaleFactor();
                        currentScale = Math.max(1f, Math.min(currentScale, 4f));
                        ivHero.setScaleX(currentScale);
                        ivHero.setScaleY(currentScale);
                        return true;
                    }
                });

        ivHero.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            if (event.getPointerCount() == 1 && event.getActionMasked() == MotionEvent.ACTION_UP
                    && currentScale <= 1f) {
                v.performClick();
            }
            return true;
        });
    }

    private void loadStats(String circuitName) {
        executor.execute(() -> {
            AppDatabase db = F1App.get().getDatabase();
            List<CachedSchedule> schedules = db.scheduleDao().getByCircuit(circuitName);

            Map<String, Integer> winsMap = new HashMap<>();
            Map<String, Integer> polesMap = new HashMap<>();
            Map<String, String> driverNames = new HashMap<>();

            String lastWinner = null;
            String lastWinnerYear = null;
            int lastYear = 0;
            int lastRound = 0;

            Type raceType = new TypeToken<List<RaceResult>>(){}.getType();
            Type qualiType = new TypeToken<List<QualifyingResult>>(){}.getType();

            for (CachedSchedule sched : schedules) {
                CachedResult raceResult = db.resultDao().get(sched.year, sched.round, "Race");
                if (raceResult != null && raceResult.resultsJson != null) {
                    try {
                        JsonObject obj = JsonParser.parseString(raceResult.resultsJson).getAsJsonObject();
                        JsonArray arr = obj.getAsJsonArray("results");
                        if (arr != null) {
                            List<RaceResult> results = gson.fromJson(arr, raceType);
                            for (RaceResult r : results) {
                                if ("1".equals(r.getPosition()) && r.getDriver() != null) {
                                    String id = r.getDriver().getDriverId();
                                    winsMap.put(id, winsMap.getOrDefault(id, 0) + 1);
                                    driverNames.put(id, r.getDriver().getFullName());
                                    if (sched.year > lastYear
                                            || (sched.year == lastYear && sched.round > lastRound)) {
                                        lastWinner = r.getDriver().getFullName();
                                        lastWinnerYear = String.valueOf(sched.year);
                                        lastYear = sched.year;
                                        lastRound = sched.round;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                CachedResult qualiResult = db.resultDao().get(sched.year, sched.round, "Qualifying");
                if (qualiResult != null && qualiResult.resultsJson != null) {
                    try {
                        JsonObject obj = JsonParser.parseString(qualiResult.resultsJson).getAsJsonObject();
                        JsonArray arr = obj.getAsJsonArray("results");
                        if (arr != null) {
                            List<QualifyingResult> results = gson.fromJson(arr, qualiType);
                            for (QualifyingResult r : results) {
                                if ("1".equals(r.getPosition()) && r.getDriver() != null) {
                                    String id = r.getDriver().getDriverId();
                                    polesMap.put(id, polesMap.getOrDefault(id, 0) + 1);
                                    driverNames.put(id, r.getDriver().getFullName());
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            List<Map.Entry<String, Integer>> winsList = new ArrayList<>(winsMap.entrySet());
            winsList.sort((a, b) -> b.getValue() - a.getValue());
            List<Map.Entry<String, Integer>> polesList = new ArrayList<>(polesMap.entrySet());
            polesList.sort((a, b) -> b.getValue() - a.getValue());

            final String fw = lastWinner;
            final String fy = lastWinnerYear;
            final List<Map.Entry<String, Integer>> fWins = winsList;
            final List<Map.Entry<String, Integer>> fPoles = polesList;
            final Map<String, String> fNames = driverNames;

            mainHandler.post(() -> displayStats(fw, fy, fWins, fPoles, fNames));
        });
    }

    private void displayStats(String lastWinner, String lastWinnerYear,
                              List<Map.Entry<String, Integer>> wins,
                              List<Map.Entry<String, Integer>> poles,
                              Map<String, String> driverNames) {
        if (lastWinner != null) {
            tvLastWinner.setText(lastWinner);
            tvLastWinnerYear.setText(lastWinnerYear);
            tvLastWinnerYear.setVisibility(View.VISIBLE);
        }

        if (!wins.isEmpty()) {
            tvWinsEmpty.setVisibility(View.GONE);
            int count = Math.min(3, wins.size());
            for (int i = 0; i < count; i++) {
                Map.Entry<String, Integer> entry = wins.get(i);
                String name = driverNames.getOrDefault(entry.getKey(), entry.getKey());
                llWinsContainer.addView(buildStatRow((i + 1) + ". " + name, "×" + entry.getValue()));
            }
        }

        if (!poles.isEmpty()) {
            tvPolesEmpty.setVisibility(View.GONE);
            int count = Math.min(3, poles.size());
            for (int i = 0; i < count; i++) {
                Map.Entry<String, Integer> entry = poles.get(i);
                String name = driverNames.getOrDefault(entry.getKey(), entry.getKey());
                llPolesContainer.addView(buildStatRow((i + 1) + ". " + name, "×" + entry.getValue()));
            }
        }
    }

    private View buildStatRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dpToPx(4));
        row.setLayoutParams(params);

        TextView tvLabel = new TextView(this);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvLabel.setText(label);
        tvLabel.setTextColor(0xFFFFFFFF);
        tvLabel.setTextSize(13f);

        TextView tvValue = new TextView(this);
        tvValue.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        tvValue.setText(value);
        tvValue.setTextColor(0xFFE10600);
        tvValue.setTextSize(13f);
        tvValue.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);

        row.addView(tvLabel);
        row.addView(tvValue);
        return row;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private String buildLocation(String locality, String country) {
        if (locality != null && !locality.isEmpty() && country != null && !country.isEmpty()) {
            return locality + ", " + country;
        } else if (country != null && !country.isEmpty()) {
            return country;
        } else if (locality != null && !locality.isEmpty()) {
            return locality;
        }
        return "";
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
