package com.f1stats;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.f1stats.util.DebugLog;
import com.f1stats.data.F1Repository;
import com.f1stats.models.CircuitPitHistory;
import com.f1stats.models.CircuitStatsResponse;
import com.f1stats.models.TrackMap;
import com.f1stats.ui.analysis.RaceAnalysisSection;
import com.f1stats.ui.charts.ChartStyle;
import com.f1stats.ui.track.TrackMapView;
import com.f1stats.util.SystemBarInsets;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TrackDetailActivity extends AppCompatActivity {

    public static final String EXTRA_CIRCUIT_IMAGE = "extra_circuit_image";
    public static final String EXTRA_CIRCUIT_NAME  = "extra_circuit_name";
    public static final String EXTRA_CIRCUIT_ID    = "extra_circuit_id";
    public static final String EXTRA_COUNTRY       = "extra_country";
    public static final String EXTRA_LOCALITY      = "extra_locality";
    public static final String EXTRA_COUNTRY_FLAG  = "extra_country_flag";

    private static final String TAG = "TrackDetail";

    private WebView webTrackMap;
    private FrameLayout flHero;
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
        SystemBarInsets.applyToContentRoot(this);

        repository = F1Repository.getInstance(this);

        String circuitImage = getIntent().getStringExtra(EXTRA_CIRCUIT_IMAGE);
        String circuitName  = getIntent().getStringExtra(EXTRA_CIRCUIT_NAME);
        String circuitId    = getIntent().getStringExtra(EXTRA_CIRCUIT_ID);
        String country      = getIntent().getStringExtra(EXTRA_COUNTRY);
        String locality     = getIntent().getStringExtra(EXTRA_LOCALITY);
        String countryFlag  = getIntent().getStringExtra(EXTRA_COUNTRY_FLAG);
        DebugLog.d("CIRCUIT_DEBUG", "TrackDetailActivity received circuitId=" + circuitId);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(circuitName != null ? circuitName : "Track Detail");
        }

        webTrackMap = findViewById(R.id.web_track_map);
        flHero = findViewById(R.id.fl_hero);
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
            loadTrackMap(circuitId, circuitName);
            loadCircuitStats(circuitId);
            loadPitHistory(circuitId);
        } else {
            tvStatsError.setText("Circuit ID unavailable — open schedule again to refresh.");
            tvStatsError.setVisibility(View.VISIBLE);
        }
    }

    // ── Track map ─────────────────────────────────────────────────────────────

    /** Generated map first; without one, the bundled SVG, then the circuit image. */
    private void loadTrackMap(String circuitId, String circuitName) {
        repository.getTrackMap(circuitId, new F1Repository.RepositoryCallback<TrackMap>() {
            @Override
            public void onSuccess(TrackMap map) {
                if (!isFinishing()) showTrackMap(map, circuitName);
            }

            @Override
            public void onError(String message) {
                DebugLog.d(TAG, "Track map unavailable: " + message);
                if (!isFinishing()) loadInteractiveTrack(circuitId);
            }
        });
    }

    private void showTrackMap(TrackMap map, String circuitName) {
        TrackMapView mapView = findViewById(R.id.track_map_view);
        mapView.setTrackMap(map);
        String name = map.circuitName != null ? map.circuitName
                : circuitName != null ? circuitName : "";
        mapView.setContentDescription(
                getString(R.string.track_map_content_description, name, map.corners.size()));

        View controls = findViewById(R.id.layout_track_map_controls);
        View legend = findViewById(R.id.layout_sector_legend);
        MaterialSwitch sectorSwitch = findViewById(R.id.switch_sectors);
        controls.setVisibility(mapView.hasSectors() ? View.VISIBLE : View.GONE);
        mapView.setShowSectors(sectorSwitch.isChecked());
        legend.setAlpha(sectorSwitch.isChecked() ? 1f : 0.3f);
        sectorSwitch.setOnCheckedChangeListener((button, checked) -> {
            mapView.setShowSectors(checked);
            legend.setAlpha(checked ? 1f : 0.3f);
        });

        TextView attribution = findViewById(R.id.tv_track_map_attribution);
        boolean hasAttribution = map.attribution != null && !map.attribution.isEmpty();
        attribution.setText(hasAttribution ? map.attribution : "");
        attribution.setVisibility(hasAttribution ? View.VISIBLE : View.GONE);

        findViewById(R.id.layout_track_map).setVisibility(View.VISIBLE);
        webTrackMap.setVisibility(View.GONE);
        flHero.setVisibility(View.GONE);
    }

    private void loadInteractiveTrack(String circuitId) {
        String[] candidates = {
            "circuits/" + circuitId + ".svg",
            "circuits/" + circuitId.replace("_", "-") + ".svg"
        };

        String svgContent = null;
        for (String candidate : candidates) {
            try {
                InputStream is = getAssets().open(candidate);
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                svgContent = new String(buffer);
                break;
            } catch (IOException ignored) {}
        }

        if (svgContent == null) {
            Log.d(TAG, "No bundled SVG for " + circuitId + ", showing fallback image");
            showFallback();
            return;
        }

        String pathData = extractPathData(svgContent);
        if (pathData == null) {
            Log.d(TAG, "Could not extract path data from SVG for " + circuitId);
            showFallback();
            return;
        }

        String metadataJson = "{}";
        try {
            InputStream metaIs = getAssets().open("track_metadata.json");
            byte[] metaBuffer = new byte[metaIs.available()];
            metaIs.read(metaBuffer);
            metaIs.close();
            JsonObject allMeta = JsonParser.parseString(new String(metaBuffer)).getAsJsonObject();
            if (allMeta.has(circuitId)) {
                metadataJson = allMeta.get(circuitId).toString();
            }
        } catch (IOException e) {
            Log.w(TAG, "No track metadata for " + circuitId);
        }

        webTrackMap.setVisibility(View.VISIBLE);
        flHero.setVisibility(View.GONE);

        WebSettings settings = webTrackMap.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        webTrackMap.setBackgroundColor(Color.TRANSPARENT);
        webTrackMap.addJavascriptInterface(new TrackJsBridge(), "Android");

        final String finalPathData = pathData
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "")
            .replace("\r", "");
        final String finalMetadata = metadataJson
            .replace("\\", "\\\\")
            .replace("'", "\\'");

        webTrackMap.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("initTrack('" + finalPathData + "', '" + finalMetadata + "');", null);
            }
        });
        webTrackMap.loadUrl("file:///android_asset/interactive_track.html");
    }

    private void showFallback() {
        webTrackMap.setVisibility(View.GONE);
        flHero.setVisibility(View.VISIBLE);
    }

    private String extractPathData(String svgContent) {
        int dStart = svgContent.indexOf(" d=\"");
        if (dStart == -1) dStart = svgContent.indexOf(" d='");
        if (dStart == -1) return null;
        dStart += 4;
        char quote = svgContent.charAt(dStart - 1);
        int dEnd = svgContent.indexOf(quote, dStart);
        if (dEnd == -1) return null;
        return svgContent.substring(dStart, dEnd);
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
                Log.e(TAG, "Failed to load circuit stats: " + message);
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

    // ── Pit strategy ──────────────────────────────────────────────────────────

    private void loadPitHistory(String circuitId) {
        repository.getCircuitPitHistory(circuitId, new F1Repository.RepositoryCallback<CircuitPitHistory>() {
            @Override
            public void onSuccess(CircuitPitHistory history) {
                if (!isFinishing()) displayPitHistory(history);
            }

            @Override
            public void onError(String message) {
                // The section is optional: it stays hidden
                DebugLog.d(TAG, "Pit history unavailable: " + message);
            }
        });
    }

    /** Average stops per finisher by season, and the most recent race's fastest stop. */
    private void displayPitHistory(CircuitPitHistory history) {
        // A circuit can host two races in one season (Red Bull Ring 2020): label those by round
        Map<Integer, Integer> racesPerSeason = new HashMap<>();
        for (CircuitPitHistory.Race race : history.races) {
            if (race.avgStopsPerFinisher != null) racesPerSeason.merge(race.season, 1, Integer::sum);
        }
        List<BarEntry> bars = new ArrayList<>();
        List<String> seasons = new ArrayList<>();
        for (CircuitPitHistory.Race race : history.races) {
            if (race.avgStopsPerFinisher == null) continue;
            bars.add(new BarEntry(bars.size(), race.avgStopsPerFinisher.floatValue()));
            seasons.add(racesPerSeason.get(race.season) > 1
                    ? getString(R.string.track_pit_season_round, race.season, race.round)
                    : String.valueOf(race.season));
        }
        View card = findViewById(R.id.card_pit_strategy);
        if (bars.isEmpty()) {
            card.setVisibility(View.GONE);
            return;
        }
        card.setVisibility(View.VISIBLE);

        BarChart chart = findViewById(R.id.chart_pit_stops);
        ChartStyle.apply(chart);
        chart.setScaleEnabled(false);
        chart.setTouchEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setGranularity(1f);
        chart.getXAxis().setLabelCount(seasons.size());
        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) {
                int i = Math.round(value);
                return i >= 0 && i < seasons.size() ? seasons.get(i) : "";
            }
        });
        BarDataSet set = new BarDataSet(bars, "");
        set.setColor(ContextCompat.getColor(this, R.color.chart_bar));
        set.setValueTextColor(ContextCompat.getColor(this, R.color.chart_axis_text));
        set.setHighlightEnabled(false);
        set.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.1f", value);
            }
        });
        BarData data = new BarData(set);
        data.setBarWidth(0.6f);
        chart.setData(data);
        chart.invalidate();

        CircuitPitHistory.Race latest = history.races.get(history.races.size() - 1);
        CircuitPitHistory.FastestStop fastest = latest.fastestStop;
        View fastestLayout = findViewById(R.id.layout_pit_fastest);
        if (fastest == null) {
            fastestLayout.setVisibility(View.GONE);
        } else {
            fastestLayout.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.tv_pit_fastest_label))
                    .setText(getString(R.string.track_pit_fastest_label, latest.season));
            ((TextView) findViewById(R.id.tv_pit_fastest_time))
                    .setText(RaceAnalysisSection.durationText(this, fastest.durationMs));
            ((TextView) findViewById(R.id.tv_pit_fastest_driver)).setText(fastest.lap != null
                    ? getString(R.string.track_pit_fastest_driver_lap, fastest.name, fastest.lap)
                    : fastest.name);
        }
        ((TextView) findViewById(R.id.tv_pit_note)).setText(
                history.durationNote != null && !history.durationNote.isEmpty()
                        ? history.durationNote : getString(R.string.pit_lane_note));
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

    private class TrackJsBridge {
        @JavascriptInterface
        public void onSectorTapped(int sectorNum) {
            Log.d(TAG, "Sector tapped: " + sectorNum);
        }
    }
}
