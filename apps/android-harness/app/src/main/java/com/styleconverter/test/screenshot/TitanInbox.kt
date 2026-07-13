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
     * Decide whether the launch intent requested TITAN *composed* capture — the
     * Round-3 WPT capture mode that renders the WHOLE fed IR document composed
     * on ONE ref-matching canvas and writes ONE PNG per test (as opposed to the
     * legacy per-component inbox capture). Layered strictly ON TOP of inbox mode
     * (composed is meaningless without a fixture to compose): the caller only
     * consults this after [isInboxModeRequested] already returned true.
     *
     *   adb shell am start -n com.styleconverter.test/.MainActivity \
     *       --ez titanInbox true --ez titanComposed true
     *
     * Same two-spelling parse as [isInboxModeRequested] (`--ez` boolean or
     * `--es "true"`) so the feeder can use either am-extra flavour. Absent /
     * falsy ⇒ false ⇒ the historical per-component inbox capture, unchanged.
     */
    fun isComposedModeRequested(boolExtra: Boolean, stringExtra: String?): Boolean {
        if (boolExtra) return true
        val s = stringExtra?.trim()?.lowercase()
        return s == "true"
    }

    /**
     * Recover the WPT test key (`wpt__<section>__<stem>`) from an inbox
     * fixture's on-device filename. The host feeder (tools/titan/feed-android.mjs)
     * pushes each per-test IR doc named `<NNNN>-<testKey>.json`, where `<NNNN>-`
     * is the FIFO index prefix that guarantees push uniqueness. We strip that
     * numeric prefix and the `.json` extension to get the bare test key, which
     * (for a WPT feed) equals the first three `__`-delimited segments of the
     * doc's root component names — i.e. the same key the web harness's
     * rootTestKey() derives (ComposedCaptureGallery.tsx) and the key
     * tools/titan/inject-wpt-block.mjs builds as `wpt__<section>__<stem>`.
     *
     * The prefix strip is unambiguous because a WPT test key always begins with
     * the letters `wpt`, never a digit, so `^\d+-` can only match the feeder's
     * index. A filename with no prefix (a manual push) round-trips unchanged.
     */
    fun composedTestKey(inboxFileName: String): String {
        // Drop the feeder's `<NNNN>-` FIFO index prefix, then the .json suffix.
        val noPrefix = inboxFileName.replace(Regex("^\\d+-"), "")
        return noPrefix.removeSuffix(".json")
    }

    /**
     * The composed-capture PNG filename for a test key: `<safe(testKey)>.png`.
     *
     * The sanitiser is the SAME character class tools/titan/inject-wpt-block.mjs
     * `safe()` and the web harness apply (`[^A-Za-z0-9._-] → _`, note the `.`
     * is KEPT), so the name the app writes is byte-identical to the one inject's
     * `diffComposedVsRef` globs for and the feeder's `composedPngName` predicts.
     * A WPT test key contains only `[A-Za-z0-9_-]`, so in practice this is the
     * identity + ".png"; keeping the exact class matters only for the defensive
     * non-WPT case and to stay in lock-step with the shared contract.
     */
    fun composedPngName(testKey: String): String =
        testKey.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png"

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
