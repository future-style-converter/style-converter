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

The runtime's JUnit suite (1006 tests) also runs from this build:
`(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest)`.

## Dynamic-capture hooks (states + media)

Android transport for the [docs/DYNAMIC_CAPTURE.md](../../docs/DYNAMIC_CAPTURE.md)
contract — both hooks are **launch intent extras** on `MainActivity`
(`test-all.sh` stays untouched; forced/width runs are manual relaunches
against the emulator it leaves running with `EMULATOR_KEEP=1`):

```bash
# forced-state run (spec 06 §6): one runtime-v1 condition
# (hover|active|focus|disabled|checked) resolved as active on EVERY
# captured component. PNG names match the base run — pull to a
# separate directory per the DYNAMIC_CAPTURE recipe.
adb shell am force-stop com.styleconverter.test
adb shell am start -n com.styleconverter.test/.MainActivity --es forceState active

# two-width media run (CAPTURE_WIDTH contract): overrides the capture
# canvas width; media min/max-width buckets evaluate against it
# (render-surface semantics — never the device screen).
adb shell am start -n com.styleconverter.test/.MainActivity --ei captureWidth 250

# deterministic motion run (CAPTURE_ANIMATION_TIME, spec 07 §5): every
# animation renders its state at absolute timeline time t, PAUSED — the
# runtime evaluates state-at-t (KeyframeAnimationDriver) instead of
# running any clock, so the frame is frozen by construction. Fractional
# seconds ride the string extra; 0 is meaningful (the initial frame,
# fill-mode/delay arithmetic included). test-all.sh forwards
# CAPTURE_ANIMATION_TIME=<s> into this extra automatically.
adb shell am start -n com.styleconverter.test/.MainActivity --es animationTime 0.5
```

Verification markers: the capture screen logs
`Capture run config: forceState=… captureWidth=… animationTime=…` to
logcat (tag `ScreenshotCapture`), and the capture canvas carries the test
tags `capture-canvas-force-state-<state>` / `…-anim-time-<s>` on hooked
runs — the native twins of the web reference's `data-force-state` /
`data-animation-time` stamps. `test-all.sh` HARD-FAILS a
`CAPTURE_ANIMATION_TIME` / `CAPTURE_FORCE_STATE` run whose logcat marker
is missing, so a hooked run can never silently degrade to a base/live
capture.
