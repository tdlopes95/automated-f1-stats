package com.f1stats.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** One card in the Home layout: its type, whether it's shown, and per-card options. */
public class HomeCardConfig {

    private final HomeCardType type;
    private boolean enabled;
    private final Map<String, String> params;

    public HomeCardConfig(@NonNull HomeCardType type, boolean enabled) {
        this(type, enabled, null);
    }

    public HomeCardConfig(@NonNull HomeCardType type, boolean enabled, Map<String, String> params) {
        this.type = type;
        this.enabled = enabled;
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
    }

    public HomeCardConfig copy() {
        return new HomeCardConfig(type, enabled, params);
    }

    @NonNull public HomeCardType getType() { return type; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    @NonNull public Map<String, String> getParams() { return params; }

    @Nullable
    public String getParam(@NonNull String key) {
        String value = params.get(key);
        return value != null && !value.isEmpty() ? value : null;
    }

    public boolean getBooleanParam(@NonNull String key) {
        return "true".equals(params.get(key));
    }

    /** Sets a param; null or empty removes it. */
    public void setParam(@NonNull String key, @Nullable String value) {
        if (value == null || value.isEmpty()) params.remove(key);
        else params.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HomeCardConfig)) return false;
        HomeCardConfig that = (HomeCardConfig) o;
        return enabled == that.enabled && type == that.type && params.equals(that.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, enabled, params);
    }

    @NonNull
    @Override
    public String toString() {
        return type + (enabled ? "" : " (hidden)") + (params.isEmpty() ? "" : " " + params);
    }
}
