plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val playStoreFile = providers.environmentVariable("HOME_ALARM_UPLOAD_STORE_FILE").orNull
val playStorePassword = providers.environmentVariable("HOME_ALARM_UPLOAD_STORE_PASSWORD").orNull
val playKeyAlias = providers.environmentVariable("HOME_ALARM_UPLOAD_KEY_ALIAS").orNull
val playKeyPassword = providers.environmentVariable("HOME_ALARM_UPLOAD_KEY_PASSWORD").orNull
val playSigningEnabled = listOf(
    playStoreFile,
    playStorePassword,
    playKeyAlias,
    playKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "uk.co.cbeesle1.homealarm.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "uk.co.cbeesle1.homealarm"
        minSdk = 30
        targetSdk = 36
        versionCode = 10003
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    val playReleaseSigning = if (playSigningEnabled) {
        signingConfigs.create("playRelease") {
            storeFile = file(requireNotNull(playStoreFile))
            storePassword = requireNotNull(playStorePassword)
            keyAlias = requireNotNull(playKeyAlias)
            keyPassword = requireNotNull(playKeyPassword)
        }
    } else {
        null
    }

    buildTypes {
        getByName("release") {
            signingConfig = playReleaseSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(project(":common"))
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")
    implementation("androidx.wear.compose:compose-material3:1.6.2")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
