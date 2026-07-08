# Android capture harness

Android test app for the Compose runtime. It loads an IR document from
`app/src/main/assets/tmpOutput.json` (regenerated, gitignored), renders
every component with the
[`com.styleconverter.runtime`](../../runtimes/compose/) library — included
into this Gradle build as `:runtime` via an out-of-tree `projectDir`, see
`settings.gradle.kts` — and auto-captures a per-component PNG on launch
(Compose `GraphicsLayer`) for the 3-way SSIM comparison. Not a product —
the runtime is; this app just feeds the visual pipeline.

**Primary entry point: [`../../test-all.sh`](../../test-all.sh)** — it
converts the fixture, copies the IR here, installs on the emulator,
launches, and pulls the screenshots.

Run it by hand:

```bash
# from the repo root — needs JDK 21 and a booted emulator/device
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :converter:run --args="convert --from css --to ir -i fixtures/visual-test.json -o out"
cp out/tmpOutput.json apps/android-harness/app/src/main/assets/
(cd apps/android-harness && ./gradlew installDebug)
adb shell am start -n com.styleconverter.test/.MainActivity
# wait for the on-screen capture summary, then:
adb pull /sdcard/Android/data/com.styleconverter.test/files/test_screenshots/ ./screenshots/
```

The runtime's JUnit suite (413 tests) also runs from this build:
`(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest)`.
