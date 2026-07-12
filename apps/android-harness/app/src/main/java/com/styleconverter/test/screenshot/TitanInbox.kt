package com.styleconverter.test.screenshot

import java.io.File

/**
 * TITAN inbox-mode pure helpers.
 *
 * These are deliberately Context-free (plain JVM) so they can be unit-tested
 * without an emulator / Robolectric — the poll-ordering rule and the
 * activation-flag parse are the two pieces most likely to regress silently, so
 * they live here where `apps/android-harness/app/src/test` (plain junit:4.13.2,
 * see app/build.gradle) can exercise them against real temp files.
 *
 * The device-side transport (nextFixtureFile / consumeFixture) in
 * [ScreenshotManager] delegates its ordering decision to [pickOldest] so the
 * tested code IS the shipped code path (no parallel re-implementation to drift).
 *
 * Rationale for the whole inbox mode: TITAN feeds thousands of WPT fixtures
 * through the CSS→IR→3-runtime pipeline. The historical Android capture path
 * (test-all.sh Step 4) rebuilds + reinstalls the APK per fixture (~30 s each).
 * Inbox mode boots the app ONCE, then the host pushes per-fixture IR JSON into
 * an on-device inbox; the app polls, renders, screenshots, and consumes — the
 * same amortisation the docstring in ScreenshotManager references.
 */
object TitanInbox {

    /**
     * Decide whether the launch intent requested titan-inbox capture mode.
     *
     * Two spellings are accepted so the host feeder can use whichever `am`
     * extra flavour is convenient and both behave identically:
     *   - `--ez titanInbox true`  → boolean extra  → [boolExtra] = true
     *   - `--es titanInbox true`  → string extra   → [stringExtra] = "true"
     *
     * The string form is trimmed + lower-cased before comparing (mirrors the
     * defensive parsing MainActivity already applies to `forceState` /
     * `animationTime`). Anything else (absent, "false", "0", garbage) ⇒ false,
     * so the DEFAULT launch stays the historical bundled-assets auto-capture.
     */
    fun isInboxModeRequested(boolExtra: Boolean, stringExtra: String?): Boolean {
        // Boolean extra wins outright when set true — the documented transport.
        if (boolExtra) return true
        // String fallback: accept the literal "true" (case/space-insensitive).
        val s = stringExtra?.trim()?.lowercase()
        return s == "true"
    }

    /**
     * Pick the oldest fixture from an inbox directory listing, or null when
     * there is nothing to process.
     *
     * FIFO by `lastModified()` matches the order the host pushes fixtures in
     * (`adb push` stamps mtime at write time). Ties on mtime — possible on
     * coarse filesystem clocks when two pushes land in the same millisecond —
     * are broken by file NAME (lexicographic) so the ordering is TOTAL and
     * deterministic; without the tiebreak `minByOrNull` would return an
     * arbitrary one of the tied files and the poll-ordering test would flake.
     *
     * Accepts a nullable array because `File.listFiles(...)` returns null when
     * the directory doesn't exist or isn't readable (the empty-inbox case).
     */
    fun pickOldest(files: Array<File>?): File? {
        if (files.isNullOrEmpty()) return null
        // compareBy chains: primary = mtime ascending, secondary = name.
        return files.minWithOrNull(
            compareBy<File> { it.lastModified() }.thenBy { it.name }
        )
    }
}
