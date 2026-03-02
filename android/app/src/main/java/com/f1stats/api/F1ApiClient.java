package com.f1stats.api;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import java.util.concurrent.TimeUnit;

public class F1ApiClient {

    // ⚠️ IMPORTANT — change this to your backend address:
    // Running backend on same PC, testing on emulator → use 10.0.2.2
    // Running backend on same PC, testing on physical phone → use your PC's local IP
    // e.g. "http://192.168.1.100:8000/" (find your IP with ipconfig on Windows)
    private static final String BASE_URL = "https://tera-maladjusted-zenobia.ngrok-free.dev";

    private static F1ApiClient instance;
    private final F1ApiService apiService;

    private F1ApiClient() {
        // Logging interceptor — shows all requests/responses in Logcat
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
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(F1ApiService.class);
    }

    // Singleton — one instance shared across the whole app
    public static synchronized F1ApiClient getInstance() {
        if (instance == null) {
            instance = new F1ApiClient();
        }
        return instance;
    }

    public F1ApiService getService() {
        return apiService;
    }
}