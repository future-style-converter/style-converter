// runtimes/compose — the Android runtime style engine, packaged as an AGP
// library module (R3). The harness app (apps/android-harness) consumes it as
// project(":runtime"); the module itself has no settings.gradle — it is
// included from apps/android-harness/settings.gradle.kts via an out-of-tree
// projectDir.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Library namespace — the engine deliberately does NOT live under the
    // harness's com.styleconverter.test application id.
    namespace = "com.styleconverter.runtime"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose runtime surface used by the per-property Appliers.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")

    // IR document decoding (core/ir).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Image loading for background-image: url() support
    // (background/BackgroundImageRenderer.kt).
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Extractor/registry unit tests are plain JVM JUnit4.
    testImplementation("junit:junit:4.13.2")
}
