package com.f1stats.ui.news;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.f1stats.R;
import com.f1stats.util.LinkOpener;
import com.f1stats.util.SystemBarInsets;
import com.f1stats.viewmodels.F1ViewModel;

/** Every headline the backend returns, newest first. Opened from the NEWS Home card. */
public class NewsActivity extends AppCompatActivity {

    /** Headlines requested from the backend. The Home card asks for the same number, so both
     *  share one cached response and the card can filter by source. */
    public static final int NEWS_LIMIT = 50;

    private F1ViewModel viewModel;
    private NewsAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news);
        SystemBarInsets.applyToContentRoot(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.news_title);
        }

        tvStatus = findViewById(R.id.tv_news_status);
        swipeRefresh = findViewById(R.id.swipe_refresh_news);
        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.f1_red));
        swipeRefresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.bg_elevated));
        swipeRefresh.setOnRefreshListener(() -> load(true));

        adapter = new NewsAdapter(item -> LinkOpener.open(this, item.link));
        RecyclerView rv = findViewById(R.id.rv_news);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(F1ViewModel.class);
        viewModel.getNews().observe(this, response -> {
            if (response == null) return;
            swipeRefresh.setRefreshing(false);
            adapter.setItems(response.items);
            showStatus(response.items.isEmpty() ? R.string.news_empty : 0);
        });
        viewModel.getNewsError().observe(this, error -> {
            if (error == null) return;
            swipeRefresh.setRefreshing(false);
            // Keep any headlines already shown; the message is for an empty screen only
            if (adapter.getItemCount() == 0) showStatus(R.string.news_error);
        });

        swipeRefresh.setRefreshing(true);
        load(false);
    }

    private void load(boolean forceRefresh) {
        showStatus(0);
        viewModel.fetchNews(NEWS_LIMIT, forceRefresh);
    }

    /** Shows a message over the list; 0 hides it. */
    private void showStatus(int messageRes) {
        if (messageRes == 0) {
            tvStatus.setVisibility(View.GONE);
        } else {
            tvStatus.setText(messageRes);
            tvStatus.setVisibility(View.VISIBLE);
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
