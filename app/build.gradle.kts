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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // UNCOMMENT THIS - You need kotlinOptions for Kotlin projects
    kotlinOptions {
        jvmTarget = "17"
    }

    // REMOVE/KEEP COMMENTED - This conflicts with kotlinOptions above
    // kotlin {
    //     jvmToolchain(24)
    // }
}

dependencies {
    implementation(libs.android.sdk)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.ui.graphics.android)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.ui.text.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // For coroutines
    implementation(libs.kotlinx.coroutines.android)
    // For HTTP requests
    implementation(libs.okhttp)
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    // ZXing for QR code generation
    implementation("com.google.zxing:core:3.5.2")
    
    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
