package com.f1stats.api;

import android.content.Context;
import android.util.Log;

import com.f1stats.SettingsManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class F1ApiClient {

    private static F1ApiClient instance;
    private final F1ApiService apiService;

    private static class RetryInterceptor implements Interceptor {
        private final int maxRetries;

        RetryInterceptor(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            int attempt = 0;
            while (true) {
                try {
                    Response response = chain.proceed(chain.request());
                    int code = response.code();
                    if (attempt >= maxRetries || code == 429 || (code >= 400 && code < 500)) {
                        return response;
                    }
                    if (code >= 500) {
                        response.close();
                        Log.w("RetryInterceptor", "5xx on attempt " + (attempt + 1) + ", retrying...");
                        Thread.sleep(1000L << attempt);
                        attempt++;
                    } else {
                        return response;
                    }
                } catch (IOException e) {
                    if (attempt >= maxRetries) throw e;
                    Log.w("RetryInterceptor", "IOException on attempt " + (attempt + 1) + ", retrying...");
                    try {
                        Thread.sleep(1000L << attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    attempt++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry interrupted", e);
                }
            }
        }
    }

    private F1ApiClient(Context context) {
        // Use context directly — do NOT call getInstance() here
        String baseUrl = SettingsManager.getInstance(context).getBaseUrl();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(3))
                .addInterceptor(logging)
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("ngrok-skip-browser-warning", "true")
                                .build()
                ))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
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