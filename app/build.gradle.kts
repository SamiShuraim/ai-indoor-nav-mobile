plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android") version "2.2.0"
    alias(libs.plugins.kotlin.compose)
}

val tileUrl: String = if (project.hasProperty("tileUrl")) {
    project.property("tileUrl") as String
} else {
    ""
}

println("tileUrl: $tileUrl")

android {
    namespace = "com.KFUPM.ai_indoor_nav_mobile"
    compileSdk = 35  // Change from 36 to 35 (36 is not stable yet)

    defaultConfig {
        applicationId = "com.KFUPM.ai_indoor_nav_mobile"
        minSdk = 24
        targetSdk = 35

        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "tileUrl", "\"$tileUrl\"")
    }

    buildFeatures {
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // UNCOMMENT THIS - You need kotlinOptions for Kotlin projects
    kotlinOptions {
        jvmTarget = "11"
    }

    // REMOVE/KEEP COMMENTED - This conflicts with kotlinOptions above
    // kotlin {
    //     jvmToolchain(24)
    // }
}

dependencies {
    implementation(libs.android.sdk)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat.v170)
    implementation(libs.material)
    implementation(libs.androidx.ui.graphics.android)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.ui.text.android)
    implementation(libs.espresso.core)
    // For coroutines
    implementation(libs.kotlinx.coroutines.android)
    // For HTTP requests
    implementation(libs.okhttp)
    // For MapLibre
    implementation("org.maplibre.gl:android-sdk:10.2.0")
    // For Bluetooth
    implementation("androidx.bluetooth:bluetooth:1.0.0-alpha02")
}
