plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.addy37.zerochill.v2preview"
    compileSdk = 36

    buildFeatures { compose = true }

    defaultConfig {
        // Separate install identity. This is NOT a replacement for the existing stable app.
        applicationId = "com.addy37.zerochill.v2preview"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-skeleton"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
}
