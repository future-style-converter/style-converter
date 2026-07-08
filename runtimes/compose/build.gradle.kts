// runtimes/compose — the Android runtime style engine, packaged as an AGP
// library module (R3). The harness app (apps/android-harness) consumes it as
// project(":runtime"); the module itself has no settings.gradle — it is
// included from apps/android-harness/settings.gradle.kts via an out-of-tree
// projectDir.
plugins {
    id("com.android.library")
    // org.jetbrains.kotlin.android removed: AGP 9's built-in Kotlin support
    // compiles Kotlin sources itself and errors if the old plugin is applied.
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Library namespace — the engine deliberately does NOT live under the
    // harness's com.styleconverter.test application id.
    namespace = "com.styleconverter.runtime"
    // compileSdk 37 (Android 17): kept in lockstep with the harness app,
    // whose androidx.core/lifecycle 2026 versions require minCompileSdk=37.
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
}

// android.kotlinOptions was removed in the AGP 9 / KGP 2.x DSL cleanup;
// the replacement is the Kotlin extension's compilerOptions block.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // Compose runtime surface used by the per-property Appliers.
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")

    // IR document decoding (core/ir).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Image loading for background-image: url() support
    // (background/BackgroundImageRenderer.kt). 2.7.0 is the final
    // io.coil-kt 2.x release; Coil 3 lives at io.coil-kt.coil3 and is a
    // source-level migration, out of scope for a version-currency pass.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Extractor/registry unit tests are plain JVM JUnit4.
    testImplementation("junit:junit:4.13.2")
}
