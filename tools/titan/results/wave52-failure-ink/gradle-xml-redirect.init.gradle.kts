// Wave 52 (lane L3 · failure-ink) — Gradle init script that redirects a
// Test task's JUnit XML (and its binary results) to a lane-private directory.
// WHY: twelve lanes run focused JVM suites on ONE shared tree, and Gradle
// wipes `runtimes/compose/build/test-results/testDebugUnitTest/` at the
// start of every test-task run — a lane that reads its XML after another
// lane's run finds only the other lane's classes (observed 2026-09-25 20:05:
// this lane's run left no XML, the directory held PercentSpacing… only; at
// 20:06 the XML was wiped between two consecutive commands of this lane).
// The redirect is applied when the task graph is READY: AGP re-assigns the
// report location during its own configuration, so a configureEach-time
// set was overridden (observed 20:06 — the binary dir moved, XML did not).
// Usage: ./gradlew … -I <this file> -Dwave52.xmlDir=<absolute dir>
gradle.taskGraph.whenReady {
    // The lane-private XML directory (required; no silent default).
    val dir = System.getProperty("wave52.xmlDir")
        ?: throw GradleException("wave52.xmlDir system property is required")
    allTasks.filterIsInstance<Test>().forEach { test ->
        // JUnit XML — what this lane's evidence reads.
        test.reports.junitXml.outputLocation.set(File(dir))
        // The binary results feed the HTML report; private too, so a
        // concurrent lane cannot corrupt this run's summary.
        test.binaryResultsDirectory.set(File("$dir/binary"))
    }
}
