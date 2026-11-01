plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.joey.findmyphone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.joey.findmyphone"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    // Garmin Connect IQ Mobile SDK (talks to the watch app via Garmin Connect Mobile)
    implementation(libs.ciq.companion.app.sdk)
}
