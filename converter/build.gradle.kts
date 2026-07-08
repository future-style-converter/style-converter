plugins {
    application
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    // kotlin-test runs on the JUnit Platform via the Kotlin stdlib's test
    // shim, picking up the same kotlinx-serialization JSON parser used by
    // production code. JUnit Jupiter chosen over 4 for matching modern
    // toolchain and clearer parameterised test ergonomics. JUnit 6 unifies
    // Jupiter and Platform version numbers (launcher is 6.x, not 1.x).
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.1")
}

application {
    mainClass.set("app.MainKt")
}

kotlin {
    jvmToolchain(21)
}

// Use the JUnit Platform so kotlin.test (which delegates) and any direct
// JUnit 5 tests both run under `./gradlew :converter:test`.
tasks.test {
    useJUnitPlatform()
}

// The CLI contract is cwd-relative: `-i fixtures/visual-test.json -o out`
// must resolve against the REPO ROOT regardless of which Gradle project
// hosts the task. In a multi-project build JavaExec defaults its working
// directory to the subproject dir (converter/), which would silently
// re-root every relative path — so pin it to the root project dir.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}


