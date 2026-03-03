package com.f1stats.api;

import android.content.Context;

import com.f1stats.SettingsManager;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

public class F1ApiClient {

    private static F1ApiClient instance;
    private final F1ApiService apiService;

    private F1ApiClient(Context context) {
        // Use context directly — do NOT call getInstance() here
        String baseUrl = SettingsManager.getInstance(context).getBaseUrl();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("ngrok-skip-browser-warning", "true")
                                .build()
                ))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(F1ApiService.class);
    }

    public static synchronized F1ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new F1ApiClient(context.getApplicationContext());
        }
        return instance;
    }

    public static synchronized void reset(Context context) {
        instance = new F1ApiClient(context.getApplicationContext());
    }

    public F1ApiService getService() {
        return apiService;
    }
}