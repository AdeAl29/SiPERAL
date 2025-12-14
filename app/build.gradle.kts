plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ade.flipbook"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ade.flipbook"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // --- CORE ANDROID ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // --- COMPOSE (UI) ---
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // --- LIBRARY FLIPBOOK ---

    // 1. Icons Extended (Untuk icon Zoom, Arrow, dll) - INI TETAP DIPAKAI
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
// untuk ekstraksi teks PDF
    // 2. PDFBox KITA HAPUS AGAR TIDAK ERROR
    // implementation("com.tom_roush:pdfbox-android:2.0.27.0") <--- DIBUANG
}