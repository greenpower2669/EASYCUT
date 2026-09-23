import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fabvidedit.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fabvidedit.app.poc"
        minSdk = 29
        targetSdk = 36
        versionCode = 39
        versionName = "0.0.6"

        // POC Android build: keep only ARM64 native FFmpeg libraries so the APK stays
        // ARM64-only for the phone; APK/AAB are published in Releases, not committed to Git.
        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    val media3Version = "1.11.0"
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-effect:$media3Version")
    implementation("androidx.media3:media3-transformer:$media3Version")

    // Maintained FFmpegKit fork: FFprobe inventory + explicit -map pre-remux for multi-stream files.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7")

    // ffmpeg-kit still references Arthenica's Smart Exception runtime classes. The maintained
    // ffmpeg-kit-full 8.1.7 POM does not declare them, so add them explicitly to the APK.
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation("com.arthenica:smart-exception-common:0.2.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
}
