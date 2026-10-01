package com.f1stats.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.f1stats.DateHelper;
import com.f1stats.R;
import com.f1stats.home.HomeCardType;
import com.f1stats.models.WeatherForecast;
import com.f1stats.util.WeatherIcon;

import java.util.Map;

/** WEEKEND_WEATHER: Open-Meteo forecast for the next race weekend, by day and by session. */
class WeatherCardHolder extends HomeCardHolder<HomeCardState.WeekendWeather> {

    private final TextView tvRace, tvMessage, tvAttribution;
    private final LinearLayout llDays, llSessions;

    WeatherCardHolder(@NonNull View itemView) {
        super(itemView, HomeCardType.WEEKEND_WEATHER);
        tvRace        = itemView.findViewById(R.id.tv_weather_race);
        tvMessage     = itemView.findViewById(R.id.tv_weather_message);
        tvAttribution = itemView.findViewById(R.id.tv_weather_attribution);
        llDays        = itemView.findViewById(R.id.ll_weather_days);
        llSessions    = itemView.findViewById(R.id.ll_weather_sessions);
    }

    @Override
    void bind(@NonNull HomeCardState.WeekendWeather state, @Nullable Map<String, String> headshots) {
        Context context = itemView.getContext();
        WeatherForecast forecast = state.forecast;
        llDays.removeAllViews();
        llSessions.removeAllViews();

        if (state.noRace || forecast == null) {
            tvRace.setVisibility(View.GONE);
            showMessage(context.getString(R.string.weather_no_race));
            tvAttribution.setVisibility(View.GONE);
            return;
        }
        tvRace.setVisibility(View.VISIBLE);
        tvRace.setText(orEmpty(forecast.raceName));

        if (!forecast.available) {
            showMessage(context.getString(R.string.weather_available_from,
                    DateHelper.formatLocalDate(forecast.availableFrom)));
            tvAttribution.setVisibility(View.GONE);
            return;
        }

        tvMessage.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(context);
        for (WeatherForecast.Day day : forecast.days) {
            View column = inflater.inflate(R.layout.item_weather_day, llDays, false);
            ((TextView) column.findViewById(R.id.tv_day_name)).setText(DateHelper.weekdayShort(day.date));
            setIcon(column.findViewById(R.id.iv_day_icon), day.weatherCode);
            ((TextView) column.findViewById(R.id.tv_day_temp)).setText(day.tempMax != null && day.tempMin != null
                    ? context.getString(R.string.weather_temp_range, round(day.tempMax), round(day.tempMin))
                    : context.getString(R.string.weather_unknown));
            ((TextView) column.findViewById(R.id.tv_day_rain)).setText(rain(context, day.rainProbabilityMax));
            llDays.addView(column);
        }
        llDays.setVisibility(forecast.days.isEmpty() ? View.GONE : View.VISIBLE);

        for (WeatherForecast.Session session : forecast.sessions) {
            View row = inflater.inflate(R.layout.item_weather_session, llSessions, false);
            setIcon(row.findViewById(R.id.iv_session_icon), session.weatherCode);
            ((TextView) row.findViewById(R.id.tv_session_name)).setText(orEmpty(session.name));
            ((TextView) row.findViewById(R.id.tv_session_time)).setText(
                    DateHelper.formatForDisplay(session.datetimeUtc, "EEE HH:mm"));
            ((TextView) row.findViewById(R.id.tv_session_temp)).setText(session.temperature != null
                    ? context.getString(R.string.weather_temp, round(session.temperature))
                    : context.getString(R.string.weather_unknown));
            ((TextView) row.findViewById(R.id.tv_session_rain)).setText(rain(context, session.rainProbability));
            llSessions.addView(row);
        }
        tvAttribution.setVisibility(View.VISIBLE);
    }

    private void showMessage(String message) {
        tvMessage.setText(message);
        tvMessage.setVisibility(View.VISIBLE);
        llDays.setVisibility(View.GONE);
    }

    private static void setIcon(ImageView view, @Nullable Integer wmoCode) {
        WeatherIcon icon = WeatherIcon.forWmoCode(wmoCode);
        if (icon != null) {
            view.setImageResource(icon.getDrawable());
            view.setVisibility(View.VISIBLE);
        } else {
            view.setImageDrawable(null);
            view.setVisibility(View.INVISIBLE);   // keep the column/row aligned
        }
    }

    private static String rain(Context context, @Nullable Integer probability) {
        return probability != null ? context.getString(R.string.weather_rain, probability)
                : context.getString(R.string.weather_unknown);
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }
}
