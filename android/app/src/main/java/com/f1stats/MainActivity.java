package com.f1stats;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.core.splashscreen.SplashScreen;

import com.f1stats.models.LiveSession;
import com.f1stats.util.SystemBarInsets;
import com.f1stats.viewmodels.F1ViewModel;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHostFragment.getNavController();

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home,
                R.id.nav_weekend,
                R.id.nav_results,
                R.id.nav_standings,
                R.id.nav_schedule
        ).build();

        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);
        bottomNav = findViewById(R.id.bottom_navigation);
        NavigationUI.setupWithNavController(bottomNav, navController);

        setupEdgeToEdge();

        // Clear Weekend badge when the user navigates to that tab
        navController.addOnDestinationChangedListener((ctrl, dest, args) -> {
            if (dest.getId() == R.id.nav_weekend) {
                bottomNav.removeBadge(R.id.nav_weekend);
            }
        });

        // Observe live session and show badge when session is active/recent
        F1ViewModel viewModel = new ViewModelProvider(this).get(F1ViewModel.class);
        viewModel.getLiveSession().observe(this, session -> updateWeekendBadge(session));
        viewModel.fetchLiveSession();
    }

    /**
     * Android 15 (targetSdk 35) is always edge-to-edge: the app bar pads for the status bar,
     * the bottom bar pads for the navigation bar, and the nav host reserves exactly the
     * bottom bar's measured height so the last item of every tab can scroll into view.
     */
    private void setupEdgeToEdge() {
        View appBar = findViewById(R.id.app_bar);
        View bottomBar = findViewById(R.id.bottom_bar_container);
        View navHost = findViewById(R.id.nav_host_fragment);

        SystemBarInsets.applyPadding(appBar, true, true, true, false);
        // Consumes the insets, so BottomNavigationView doesn't add its own bottom padding too
        SystemBarInsets.applyPadding(bottomBar, true, false, true, true);

        ViewCompat.setOnApplyWindowInsetsListener(navHost, (v, insets) -> {
            Insets i = insets.getInsets(SystemBarInsets.TYPES);
            v.setPadding(i.left, v.getPaddingTop(), i.right, v.getPaddingBottom());
            return insets;
        });

        bottomBar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int height = b - t;
            if (navHost.getPaddingBottom() != height) {
                navHost.setPadding(navHost.getPaddingLeft(), navHost.getPaddingTop(),
                        navHost.getPaddingRight(), height);
            }
        });
    }

    private void updateWeekendBadge(LiveSession session) {
        if (session == null || session.getSessionKey() == 0) {
            bottomNav.removeBadge(R.id.nav_weekend);
            return;
        }

        // Don't badge if we're already on the Weekend tab
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.nav_weekend) {
            return;
        }

        boolean shouldShow = session.isLive();
        if (!shouldShow && session.getLastUpdated() != null) {
            try {
                String raw = session.getLastUpdated().replaceAll("\\.\\d+Z?$", "");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date updated = sdf.parse(raw);
                if (updated != null) {
                    long sixHoursMs = 6L * 60 * 60 * 1000;
                    shouldShow = (System.currentTimeMillis() - updated.getTime()) < sixHoursMs;
                }
            } catch (Exception ignored) {}
        }

        if (shouldShow) {
            BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_weekend);
            badge.setVisible(true);
            badge.clearNumber();
        } else {
            bottomNav.removeBadge(R.id.nav_weekend);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            navController.navigate(R.id.nav_settings);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
