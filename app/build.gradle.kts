plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lilyai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lilyai"
        // 30 = Android 11, the minimum for AccessibilityService.takeScreenshot()
        minSdk = 30
        targetSdk = 34
        // Rises with every build, so a newer APK is never refused as a downgrade.
        versionCode = (System.currentTimeMillis() / 1000).toInt()
        // The release workflow passes the version it was asked for (-PversionName=...).
        versionName = (project.findProperty("versionName") as String?) ?: "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // On-device text recognition (Latin script). The model ships inside the APK and
    // runs offline.
    implementation("com.google.mlkit:text-recognition:16.0.0")
    // The slide-out menu on the left.
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
}
