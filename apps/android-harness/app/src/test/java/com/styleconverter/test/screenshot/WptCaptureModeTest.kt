package com.styleconverter.test.screenshot

import com.styleconverter.runtime.core.renderer.shouldSuppressSynthesizedName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the WPT-capture placeholder-suppression gate
 * ([shouldSuppressSynthesizedName] in the runtime's core/renderer). This is the
 * one piece of the WPT-mode behaviour testable WITHOUT an emulator/Robolectric:
 * the pure decision the `@Composable` `PlaceholderContent` early-return calls.
 *
 * Proves the suppression fires ONLY for the nameless-empty case in WPT mode and
 * NEVER in default mode — i.e. real text always renders, and the baseline
 * (default-mode) placeholder path is untouched.
 *
 * Plain junit:4.13.2 (app/build.gradle testImplementation), no Android runtime;
 * the runtime engine is on the classpath via `implementation(project(":runtime"))`.
 */
class WptCaptureModeTest {

    // ── WPT mode ON ─────────────────────────────────────────────────────────

    @Test
    fun wptMode_suppressesSynthesizedName_whenRawTextNull() {
        // Nameless-empty component in WPT capture: the synthesized component
        // name would otherwise paint over an empty styled box → suppress it,
        // matching the Chromium browser-ref (empty background-color box).
        assertTrue(shouldSuppressSynthesizedName(wptCaptureMode = true, rawText = null))
    }

    @Test
    fun wptMode_suppressesSynthesizedName_whenRawTextEmpty() {
        // Empty-string rawText is the same nameless case as null (isNullOrEmpty).
        assertTrue(shouldSuppressSynthesizedName(wptCaptureMode = true, rawText = ""))
    }

    @Test
    fun wptMode_keepsRealText() {
        // Real IR `_text`/rawText content must STILL render in WPT mode — only
        // the synthesized name is suppressed, never genuine text.
        assertFalse(shouldSuppressSynthesizedName(wptCaptureMode = true, rawText = "Hello"))
    }

    // ── Default mode OFF (baseline behaviour unchanged) ─────────────────────

    @Test
    fun defaultMode_neverSuppresses_evenWhenNameless() {
        // Default/product/baseline path: the nameless placeholder still renders
        // exactly as before, so the committed 327-pair baseline is unaffected.
        assertFalse(shouldSuppressSynthesizedName(wptCaptureMode = false, rawText = null))
        assertFalse(shouldSuppressSynthesizedName(wptCaptureMode = false, rawText = ""))
    }

    @Test
    fun defaultMode_keepsRealText() {
        // Real text renders in default mode too (sanity: both flags off-path).
        assertFalse(shouldSuppressSynthesizedName(wptCaptureMode = false, rawText = "Hello"))
    }
}
