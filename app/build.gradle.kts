import groovy.json.JsonSlurper

/**
 * Firebase settings for one package, read from app/google-services.json (downloaded from the
 * Firebase console). Read here instead of with the google-services plugin, so the app still builds
 * before the file exists. Returns apiKey, appId, projectId, webClientId (empty when missing).
 */
fun firebaseConfig(jsonFile: File, packageName: String): List<String> {
    val text = System.getenv("WM_GOOGLE_SERVICES_JSON")?.takeIf { it.isNotBlank() }
        ?: (if (jsonFile.exists()) jsonFile.readText() else null)
        ?: return listOf("", "", "", "")
    @Suppress("UNCHECKED_CAST")
    val root = JsonSlurper().parseText(text) as Map<String, Any?>
    val projectId = (root["project_info"] as Map<String, Any?>)["project_id"] as String
    val clients = root["client"] as List<Map<String, Any?>>
    val client = clients.firstOrNull {
        val info = it["client_info"] as Map<String, Any?>
        (info["android_client_info"] as Map<String, Any?>)["package_name"] == packageName
    } ?: return listOf("", "", "", "")
    val appId = (client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"] as String
    val apiKey = ((client["api_key"] as List<Map<String, Any?>>).first())["current_key"] as String
    val web = (client["oauth_client"] as List<Map<String, Any?>>? ?: emptyList()).firstOrNull { (it["client_type"] as Number).toInt() == 3 }
        ?: clients.flatMap { (it["oauth_client"] as List<Map<String, Any?>>? ?: emptyList()) }.firstOrNull { (it["client_type"] as Number).toInt() == 3 }
    return listOf(apiKey, appId, projectId, (web?.get("client_id") as String?) ?: "")
}

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
        versionCode = 18
        versionName = "1.1.0"
    }
    buildFeatures { buildConfig = true }

    // The upload key comes from environment variables (GitHub secrets in the release workflow);
    // it is never stored in the repository.
    val keystorePath = System.getenv("WM_KEYSTORE_PATH")
    signingConfigs {
        // A fixed debug key (not secret) so Google sign-in works in every test build from CI.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
            val fb = firebaseConfig(file("google-services.json"), "in.wealthmanager.app.debug")
            buildConfigField("String", "FB_API_KEY", "\"${fb[0]}\"")
            buildConfigField("String", "FB_APP_ID", "\"${fb[1]}\"")
            buildConfigField("String", "FB_PROJECT_ID", "\"${fb[2]}\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${fb[3]}\"")
        }
        release {
            val fb = firebaseConfig(file("google-services.json"), "in.wealthmanager.app")
            buildConfigField("String", "FB_API_KEY", "\"${fb[0]}\"")
            buildConfigField("String", "FB_APP_ID", "\"${fb[1]}\"")
            buildConfigField("String", "FB_PROJECT_ID", "\"${fb[2]}\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${fb[3]}\"")
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

    // Google sign-in (Credential Manager) and the account/trial record in Firebase.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
}
