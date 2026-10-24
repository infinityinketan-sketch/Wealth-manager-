plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.ledger"
    compileSdk = 36

    defaultConfig {
        // Permanent Play Store id: never change it after the first upload.
        applicationId = "in.wealthmanager.app"
        minSdk = 26
        // Google Play requires Android 16 (API 36) for new apps and updates from 31 Aug 2026.
        targetSdk = 36
        versionCode = 17
        versionName = "1.0.0"
    }

    // The upload key comes from environment variables (GitHub secrets in the release workflow);
    // it is never stored in the repository.
    val keystorePath = System.getenv("WM_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("WM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WM_KEY_ALIAS")
                keyPassword = System.getenv("WM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Test builds install next to the Play version instead of replacing it.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-test"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.biometric:biometric:1.1.0")

    // Google Play Billing for the one-time Pro unlock (Play requires version 8 or later).
    implementation("com.android.billingclient:billing:8.0.0")

    testImplementation("junit:junit:4.13.2")
}
