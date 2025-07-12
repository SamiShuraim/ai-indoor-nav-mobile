plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose) // if you use compose
}

android {
    namespace = "com.KFUPM.ai_indoor_nav_mobile" // 👉 Use your actual package name
    compileSdk = 35 // ✅ Fixes the AAR metadata issue

    defaultConfig {
        applicationId = "com.KFUPM.ai_indoor_nav_mobile" // 👉 Use your actual package name
        minSdk = 24 // ✅ Good for MapLibre
        targetSdk = 35

        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17) // ✅ Modern replacement for old jvmTarget
    }
}

dependencies {
    implementation("org.maplibre.gl:android-sdk:11.11.0") // ✅ Latest stable MapLibre
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation(libs.androidx.ui.graphics.android)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.ui.text.android)
    implementation(libs.espresso.core)
}
