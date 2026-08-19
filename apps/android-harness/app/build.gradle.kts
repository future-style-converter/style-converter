plugins {
    id("com.android.application")
    // org.jetbrains.kotlin.android removed: AGP 9's built-in Kotlin support
    // compiles Kotlin sources itself and errors if the old plugin is applied.
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.styleconverter.test"
    // compileSdk 37 (Android 17): required by androidx.core 1.19 and
    // androidx.lifecycle 2.11 AAR metadata (minCompileSdk=37).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.styleconverter.test"
        minSdk = 24
        // targetSdk stays one behind compileSdk on purpose: bumping it opts
        // the harness into Android 17 runtime behavior changes that the
        // committed visual baselines have not been validated against.
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
    // The runtime style engine (repo-root/runtimes/compose), wired in via
    // settings.gradle.kts as an out-of-tree module. The harness only keeps
    // MainActivity + screenshot/ui glue; all styling lives in the library.
    implementation(project(":runtime"))

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Image loading library for background-image: url()/data-URI support.
    // Coil 3 (io.coil-kt.coil3 coordinates — wave-8 #36 migration), kept
    // in lockstep with the :runtime library's version. The OkHttp network
    // artifact self-registers via ServiceLoader in 3.x, so http(s) images
    // keep loading with the default singleton loader.
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}
