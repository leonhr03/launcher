plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cleanlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cleanlauncher"
        minSdk = 26
        targetSdk = 35
        // Steigt mit jedem Build automatisch, damit Updates ohne Deinstallieren gehen
        val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()
        versionCode = 100 + run
        versionName = "2.$run"
    }

    signingConfigs {
        create("fixed") {
            // Fester Schlüssel: jede neue Version lässt sich über die alte installieren
            storeFile = file("cleanlauncher.keystore")
            storePassword = "cleanlauncher"
            keyAlias = "cleanlauncher"
            keyPassword = "cleanlauncher"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Mit dem Debug-Schlüssel signiert, damit die APK direkt installiert werden kann.
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        disable += setOf("ProtectedPermissions")
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
}
