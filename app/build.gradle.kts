plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.webapp.crazyshit"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
        compose = true
    }

    defaultConfig {
        // Keep the existing release application ID so stable releases upgrade the main app.
        applicationId = "com.addy37.crazyshitunofficial"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 5_001_000
        versionName = System.getenv("APP_VERSION_NAME") ?: "5.1.0-beta.1"
        buildConfigField("boolean", "SHITTOK_POOL_DIAGNOSTICS", System.getenv("SHITTOK_POOL_DIAGNOSTICS") ?: "false")
        buildConfigField("String", "SOURCE_REVISION", "\"${System.getenv("ZEROCHILL_SOURCE_SHA") ?: "local"}\"")
        buildConfigField(
            "String",
            "FEEDBACK_ENDPOINT",
            "\"${System.getenv("FEEDBACK_ENDPOINT") ?: ""}\""
        )
        buildConfigField(
            "String",
            "FEEDBACK_ANON_KEY",
            "\"${System.getenv("FEEDBACK_ANON_KEY") ?: ""}\""
        )
        buildConfigField(
            "String",
            "ANALYTICS_ENDPOINT",
            "\"${System.getenv("ANALYTICS_ENDPOINT") ?: "https://fketutffusxgjxjlckci.supabase.co/functions/v1/analytics-ingest"}\""
        )
        buildConfigField(
            "String",
            "ACCOUNT_SUPABASE_URL",
            "\"${System.getenv("SUPABASE_URL") ?: "https://fketutffusxgjxjlckci.supabase.co"}\""
        )
        buildConfigField(
            "String",
            "ACCOUNT_SUPABASE_PUBLISHABLE_KEY",
            "\"${System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: ""}\""
        )
        buildConfigField(
            "String",
            "SOURCE_CONFIG_ENDPOINT",
            "\"${System.getenv("SOURCE_CONFIG_ENDPOINT") ?: ""}\""
        )
        buildConfigField(
            "String",
            "SOURCE_CONFIG_PUBLISHABLE_KEY",
            "\"${System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: ""}\""
        )
    }

    val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

    if (
        releaseKeystorePath != null &&
        releaseKeystorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
    ) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Betas remain side-by-side with stable while using the same persistent signing key.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "ZeroChill Beta")
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Native Robolectric surface fixtures share a JVM with the complete regression suite.
// Budget test memory explicitly; this does not change the application's heap or APK.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    maxHeapSize = "1g"
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.16")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.webkit:webkit:1.17.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("org.jsoup:jsoup:1.23.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.bumptech.glide:avif-integration:4.16.0")

    baselineProfile(project(":baselineprofile"))

    val media3Version = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-common-ktx:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-inspector-frame:$media3Version")
}

baselineProfile {
    automaticGenerationDuringBuild = false
    dexLayoutOptimization = true
}
