# com.styleconverter.runtime — the Android runtime

The Android runtime style engine: an AGP library module that turns Style
Converter IR into Jetpack Compose UI, one **Config / Extractor / Applier**
triplet per property under
`src/main/java/com/styleconverter/runtime/<category>/` (the canonical
33-category tree shared with `runtimes/web` and `runtimes/swiftui` — see
the repo-root `CLAUDE.md` for the per-property contract). The module has
no `settings.gradle` of its own: `apps/android-harness/settings.gradle.kts`
includes it as `:runtime` via an out-of-tree `projectDir`.

```bash
# from the repo root — needs JDK 21 + Android SDK
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest)   # JUnit suite (1269 tests)
```

Rendered and screenshot-tested by [`apps/android-harness/`](../../apps/android-harness/)
via `./test-all.sh`.
