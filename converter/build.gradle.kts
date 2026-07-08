plugins {
    application
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // kotlin-test runs on the JUnit 5 platform via the Kotlin stdlib's test
    // shim, picking up the same kotlinx-serialization JSON parser used by
    // production code. JUnit 5 chosen over 4 for matching modern toolchain
    // (Java 23+) and clearer parameterised test ergonomics.
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
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

// The CLI contract is cwd-relative: `-i examples/visual-test.json -o out`
// must resolve against the REPO ROOT regardless of which Gradle project
// hosts the task. In a multi-project build JavaExec defaults its working
// directory to the subproject dir (converter/), which would silently
// re-root every relative path — so pin it to the root project dir.
tasks.named<JavaExec>("run") {
    workingDir = rootDir
}


