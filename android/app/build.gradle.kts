plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.f1stats"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.f1stats"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Android core
    implementation ("androidx.appcompat:appcompat:1.7.0")
    implementation ("com.google.android.material:material:1.12.0")
    implementation ("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Navigation (bottom nav bar between screens)
    implementation ("androidx.navigation:navigation-fragment:2.7.7")
    implementation ("androidx.navigation:navigation-ui:2.7.7")

    // ViewModel + LiveData (MVVM architecture)
    implementation ("androidx.lifecycle:lifecycle-viewmodel:2.8.3")
    implementation ("androidx.lifecycle:lifecycle-livedata:2.8.3")

    // Retrofit (HTTP calls to our Python backend)
    implementation ("com.squareup.retrofit2:retrofit:2.11.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.11.0")

    // OkHttp (networking layer under Retrofit + logging)
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation(libs.activity)

    // Testing
    testImplementation ("junit:junit:4.13.2")
    androidTestImplementation ("androidx.test.ext:junit:1.2.1")
    androidTestImplementation ("androidx.test.espresso:espresso-core:3.6.1")

    // Shimmer for "loading..."
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")
}