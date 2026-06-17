package com.f1stats;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.f1stats.data.F1Repository;
import com.f1stats.models.CircuitStatsResponse;

public class TrackDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CIRCUIT_IMAGE = "extra_circuit_image";
    public static final String EXTRA_CIRCUIT_NAME  = "extra_circuit_name";
    public static final String EXTRA_CIRCUIT_ID    = "extra_circuit_id";
    public static final String EXTRA_COUNTRY       = "extra_country";
    public static final String EXTRA_LOCALITY      = "extra_locality";
    public static final String EXTRA_COUNTRY_FLAG  = "extra_country_flag";

    private ImageView ivHero;
    private ScaleGestureDetector scaleDetector;
    private float currentScale = 1f;

    private ProgressBar pbLoading;
    private TextView tvTotalRaces, tvFirstGP;
    private TextView tvMostWinsDriver, tvMostWinsCount;
    private TextView tvMostPolesDriver, tvMostPolesCount;
    private TextView tvTopConstructor, tvTopConstructorCount;
    private TextView tvLapRecordTime, tvLapRecordDriver;
    private TextView tvStatsError;

    private F1Repository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_detail);

        repository = new F1Repository(
                F1App.get().getDatabase(),
                com.f1stats.api.F1ApiClient.getInstance(this).getService()
        );

        String circuitImage = getIntent().getStringExtra(EXTRA_CIRCUIT_IMAGE);
        String circuitName  = getIntent().getStringExtra(EXTRA_CIRCUIT_NAME);
        String circuitId    = getIntent().getStringExtra(EXTRA_CIRCUIT_ID);
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
        tvLocation.setText(buildLocation(locality, country));
        if (countryFlag != null && !countryFlag.isEmpty()) {
            ivFlag.setVisibility(View.VISIBLE);
            Glide.with(this).load(countryFlag).into(ivFlag);
        }

        pbLoading          = findViewById(R.id.pb_stats_loading);
        tvTotalRaces       = findViewById(R.id.tv_total_races);
        tvFirstGP          = findViewById(R.id.tv_first_gp);
        tvMostWinsDriver   = findViewById(R.id.tv_most_wins_driver);
        tvMostWinsCount    = findViewById(R.id.tv_most_wins_count);
        tvMostPolesDriver  = findViewById(R.id.tv_most_poles_driver);
        tvMostPolesCount   = findViewById(R.id.tv_most_poles_count);
        tvTopConstructor   = findViewById(R.id.tv_top_constructor);
        tvTopConstructorCount = findViewById(R.id.tv_top_constructor_count);
        tvLapRecordTime    = findViewById(R.id.tv_lap_record_time);
        tvLapRecordDriver  = findViewById(R.id.tv_lap_record_driver);
        tvStatsError       = findViewById(R.id.tv_stats_error);

        if (circuitId != null && !circuitId.isEmpty()) {
            loadCircuitStats(circuitId);
        } else {
            tvStatsError.setText("Circuit ID unavailable — open schedule again to refresh.");
            tvStatsError.setVisibility(View.VISIBLE);
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

    private void loadCircuitStats(String circuitId) {
        pbLoading.setVisibility(View.VISIBLE);

        repository.getCircuitStats(circuitId, new F1Repository.RepositoryCallback<CircuitStatsResponse>() {
            @Override
            public void onSuccess(CircuitStatsResponse stats) {
                pbLoading.setVisibility(View.GONE);
                displayCircuitStats(stats);
            }

            @Override
            public void onError(String message) {
                pbLoading.setVisibility(View.GONE);
                tvStatsError.setText("Could not load stats");
                tvStatsError.setVisibility(View.VISIBLE);
                Log.e("TRACK_STATS", "Failed to load circuit stats: " + message);
            }
        });
    }

    private void displayCircuitStats(CircuitStatsResponse stats) {
        if (stats.totalRaces > 0) {
            tvTotalRaces.setText(String.valueOf(stats.totalRaces));
            tvFirstGP.setText(String.valueOf(stats.firstGPYear));
        }

        if (stats.mostWins != null) {
            tvMostWinsDriver.setText(stats.mostWins.name);
            tvMostWinsCount.setText("×" + stats.mostWins.count);
        }

        if (stats.mostPoles != null) {
            tvMostPolesDriver.setText(stats.mostPoles.name);
            tvMostPolesCount.setText("×" + stats.mostPoles.count);
        }

        if (stats.mostConstructorWins != null) {
            tvTopConstructor.setText(stats.mostConstructorWins.name);
            tvTopConstructorCount.setText("×" + stats.mostConstructorWins.count);
        }

        if (stats.lapRecord != null) {
            tvLapRecordTime.setText(stats.lapRecord.time);
            tvLapRecordDriver.setText(stats.lapRecord.name + " (" + stats.lapRecord.year + ")");
        }
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
}
