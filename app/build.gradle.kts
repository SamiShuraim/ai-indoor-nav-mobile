plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    compileSdk = 35

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation("org.maplibre.gl:android-sdk:11.11.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation(libs.androidx.ui.graphics.android)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.ui.text.android)
    implementation(libs.espresso.core)
}
