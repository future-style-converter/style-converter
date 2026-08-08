package com.styleconverter.test.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Manages screenshot capture and storage for component testing.
 *
 * On Android 11+, screenshots are saved to app-specific external files directory:
 *   /sdcard/Android/data/com.styleconverter.test/files/test_screenshots/
 *
 * On older Android versions, screenshots are saved to:
 *   /sdcard/test_screenshots/
 *
 * Both locations are accessible via adb pull.
 */
class ScreenshotManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenshotManager"
        private const val SCREENSHOT_DIR = "test_screenshots"
    }

    private val screenshotDir: File by lazy {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - use app-specific external files directory
            File(context.getExternalFilesDir(null), SCREENSHOT_DIR)
        } else {
            // Older Android - use shared external storage
            File(Environment.getExternalStorageDirectory(), SCREENSHOT_DIR)
        }
        dir.also {
            if (!it.exists()) {
                val created = it.mkdirs()
                Log.i(TAG, "Created screenshot directory: ${it.absolutePath}, success: $created")
            }
        }
    }

    /**
     * Clears all existing screenshots from the test_screenshots folder.
     * Call this at app startup before capturing new screenshots.
     */
    fun clearScreenshots(): Int {
        var deletedCount = 0
        try {
            if (screenshotDir.exists()) {
                screenshotDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.extension == "png" || file.extension == "jpg")) {
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                }
            }
            Log.i(TAG, "Cleared $deletedCount existing screenshots from ${screenshotDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing screenshots: ${e.message}")
        }
        return deletedCount
    }

    /**
     * Saves a bitmap as a PNG screenshot.
     *
     * @param bitmap The bitmap to save
     * @param componentName The component name (used for filename)
     * @param index The component index (for ordering)
     * @return The saved file, or null if saving failed
     */
    fun saveScreenshot(bitmap: Bitmap, componentName: String, index: Int): File? {
        return try {
            // Ensure directory exists
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs()
            }

            // Sanitize filename
            val safeName = componentName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val filename = String.format("%03d_%s.png", index, safeName)
            val file = File(screenshotDir, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            Log.i(TAG, "Saved screenshot: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error saving screenshot for $componentName: ${e.message}", e)
            null
        }
    }

    /**
     * Saves a COMPOSED capture bitmap for a WPT test (TITAN Round 3).
     *
     * Unlike [saveScreenshot] (per-component, `%03d_<safeName>.png`), the
     * composed path writes ONE PNG per fed document named `<safe(testKey)>.png`
     * — the exact name tools/titan/inject-wpt-block.mjs's `diffComposedVsRef`
     * globs for and the feeder pulls. Sanitisation is delegated to
     * [TitanInbox.composedPngName] so the on-device name matches inject's
     * `safe()` rule byte-for-byte (the `.` is preserved). Written into the
     * SAME [screenshotDir] the feeder already pulls from, so no host-side path
     * changes are needed.
     *
     * @param bitmap  the whole composed canvas, captured once via PixelCopy
     * @param testKey the WPT test key `wpt__<section>__<stem>`
     * @return the saved file, or null if writing failed
     */
    fun saveComposedScreenshot(bitmap: Bitmap, testKey: String): File? {
        return try {
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs()
            }
            val filename = TitanInbox.composedPngName(testKey)
            val file = File(screenshotDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.i(TAG, "Saved composed screenshot: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error saving composed screenshot for $testKey: ${e.message}", e)
            null
        }
    }

    /**
     * Gets the screenshot directory path.
     */
    fun getScreenshotPath(): String = screenshotDir.absolutePath

    /**
     * Gets the adb pull command to retrieve screenshots.
     */
    fun getAdbPullCommand(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "adb pull /sdcard/Android/data/com.styleconverter.test/files/$SCREENSHOT_DIR/ ./screenshots/"
        } else {
            "adb pull /sdcard/$SCREENSHOT_DIR/ ./screenshots/"
        }
    }

    /**
     * Checks if we have write permission to the screenshot directory.
     */
    fun hasWritePermission(): Boolean {
        return try {
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs()
            }
            // Try to create a test file to verify write access
            val testFile = File(screenshotDir, ".test_write")
            val canWrite = testFile.createNewFile()
            if (canWrite) {
                testFile.delete()
            }
            canWrite || screenshotDir.canWrite()
        } catch (e: Exception) {
            Log.e(TAG, "Permission check failed: ${e.message}")
            false
        }
    }

    /**
     * Gets the count of screenshots in the directory.
     */
    fun getScreenshotCount(): Int {
        return screenshotDir.listFiles()?.count {
            it.isFile && it.extension == "png"
        } ?: 0
    }

    /**
     * Returns true when a component name belongs to the B-EXT typography
     * probe set (B8/B9/B10), per
     * docs/reports/COMPARE_METRICS_B8-B10.md Section 2 naming convention.
     *
     * The capture loop uses this to decide whether to scale the captured
     * bitmap 4× before saving (B8 needs 4× resolution so a 0.25-px CSS
     * baseline shift surfaces as a 1-px shift in the buffer).
     */
    fun isProbeComponent(componentName: String): Boolean {
        // Underscore-suffix match keeps "B8_Serif_AVATAR_16" in the set
        // but excludes a hypothetical "B8X_..." that isn't a probe.
        return componentName.startsWith("B8_") ||
               componentName.startsWith("B9_") ||
               componentName.startsWith("B10_")
    }

    /**
     * Hi-res variant for B-EXT typography probes
     * (docs/reports/COMPARE_METRICS_B8-B10.md Section 1.1 + Section 7 step 7).
     *
     * Composables on the standard 160-dpi emulator render at 1 dp = 1 px,
     * so the captured Bitmap matches CSS coordinates pixel-for-pixel. To
     * reach the 4× resolution the spec requires, we upscale the captured
     * Bitmap with `Bitmap.createScaledBitmap(..., filter=true)` before
     * saving — bilinear filtering preserves edge AA structure better than
     * nearest-neighbour, which is what the FFT-based B9 classifier needs
     * to distinguish greyscale-AA from subpixel-AA.
     *
     * The asymmetry vs iOS (which re-renders at 4× via ImageRenderer.scale)
     * is intentional: Android's ComponentRenderer composes via Compose,
     * which doesn't expose a per-render scale knob; changing the emulator
     * density mid-run would invalidate every other capture in the same
     * session. Bilinear upscale of an already-laid-out 1× bitmap loses
     * some hinting fidelity vs a true 4× re-render, but for B8 (baseline
     * column scan) and B10 (glyph-edge projection) the upscaled buffer is
     * adequate. B9 (AA-spectrum classification) results from Android may
     * be less reliable than iOS/web — flagged in the metric output as
     * `'unknown'` rather than a confidently-wrong label.
     *
     * TODO[B-EXT round 91+]: requires Android emulator validation pass
     * before the Android branch of probe-text-metrics.sh can call into
     * here. The web pipeline already produces real probe data via
     * capture-screenshots-hires.mjs.
     */
    // ── TITAN Phase 1 inbox-polling mode ────────────────────────────────────
    //
    // docs/reports/TITAN_ARCHITECTURE.md §6.4 — the WPT bucket-A pass needs to
    // amortise the (slow) emulator boot + APK install + activity launch
    // across many fixtures rather than paying ~30 s of overhead per
    // fixture. The app boots ONCE; the orchestrator pushes per-fixture
    // IR JSON via `adb push` into an inbox directory; the app polls,
    // renders, screenshots, and deletes the inbox file. Estimated
    // wall time for full bucket-A drops from ~50 hr to ~5-7 hr.
    //
    // TODO[TITAN Phase 1.5]: requires Android emulator validation pass.
    // Phase 1 (TITAN-IMPL-1) ships these helpers as code-only because the
    // emulator validation environment couldn't be reliably booted within
    // the time budget. The host-side counterpart (tools/titan/feed-android.mjs)
    // is also Phase 1.5 work — it pushes fixtures via:
    //   adb push <ir.json> /sdcard/Android/data/com.styleconverter.test/files/inbox/
    //
    // Inbox path mirrors the screenshot directory layout so existing
    // adb-pull tooling can reach it without bespoke setup.

    /** TITAN inbox directory (Android 11+). */
    private val inboxDir: File by lazy {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            File(context.getExternalFilesDir(null), "inbox")
        } else {
            File(Environment.getExternalStorageDirectory(), "inbox")
        }
        dir.also { if (!it.exists()) it.mkdirs() }
    }

    /**
     * TITAN @font-face sandbox (wave-35 lane B2).
     *
     * A SIBLING of [inboxDir], not a subdirectory: [nextFixtureFile] lists the
     * inbox for `*.json`, so a font living under it would be inert but
     * confusing. The host feeder (tools/titan/feed-android.mjs) `adb push`es
     * each font file the fed document declares to
     * `<this dir>/<fontFaces[].src>`, preserving the corpus-relative path
     * VERBATIM — which is the whole contract with
     * [com.styleconverter.runtime.typography.font.DocumentFontRegistry]: it
     * resolves `File(fontsDir, face.src)` with no name mangling, so there is
     * no escaping rule that could drift between host and device.
     *
     * Note it is NOT created here. Absence is a meaningful state — it means
     * this run's feeder pushed no faces — and the registry's decline path
     * already reports it with the family AND the path.
     */
    fun getFontsDir(): File =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            File(context.getExternalFilesDir(null), "fonts")
        } else {
            File(Environment.getExternalStorageDirectory(), "fonts")
        }

    /**
     * Returns the oldest *.json fixture currently in the inbox, or null
     * if the inbox is empty. Oldest-first matches the FIFO semantics the
     * host pushes in.
     *
     * The caller is responsible for calling [consumeFixture] AFTER a
     * successful render so a crash mid-render doesn't drop the work
     * silently — the next poll picks the same file up.
     */
    fun nextFixtureFile(): File? {
        // Ordering (oldest-first, name tiebreak) lives in TitanInbox.pickOldest
        // so the shipped path and the unit-tested path are the SAME code — the
        // filter here (regular *.json only) stays local since it's about the
        // on-device inbox contract, not the ordering rule under test.
        val files = inboxDir.listFiles { f -> f.isFile && f.extension == "json" }
        return TitanInbox.pickOldest(files)
    }

    /**
     * Delete a fixture from the inbox. Idempotent — the file may already
     * be gone if the host re-pushed before this call landed.
     */
    fun consumeFixture(file: File) {
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "consumeFixture: ${e.message}")
        }
    }

    /** TITAN inbox directory path (for diagnostics). */
    fun getInboxPath(): String = inboxDir.absolutePath

    fun saveProbeScreenshot(bitmap: Bitmap, componentName: String, index: Int): File? {
        return try {
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs()
            }
            val safeName = componentName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            // The on-device file name is just the component name; the
            // adb-pull script renames to `Android__<name>.png` on the
            // host so it matches the iOS__/web__ pattern that
            // compute-text-metrics.mjs's discoverProbes() regex expects.
            // (See tools/visual/probe-text-metrics.sh for the rename rule once
            // the Android branch is wired — TODO[B-EXT round 91+] above.)
            val filename = String.format("Android__%s.png", safeName)
            val file = File(screenshotDir, filename)
            // 4.0× — Section 1.1 of the spec. Bilinear filter on
            // (filter=true) so the AA structure of edges survives upscaling.
            // Hardcoded scale because the metric helpers expect exactly 4×
            // (the px conversion in B8/B10 is `value / 4`).
            val w = (bitmap.width * 4).coerceAtLeast(1)
            val h = (bitmap.height * 4).coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.i(TAG, "Saved probe screenshot @4×: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error saving probe screenshot for $componentName: ${e.message}", e)
            null
        }
    }
}
