package com.styleconverter.runtime.core.renderer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure composed-WPT text line-box decisions (WptCaptureMode.kt,
 * FIX 2 of TITAN Round 4b): [composedDefaultLineHeightPx] and
 * [composedPlaceholderTextPaddingDp]. These pin the placeholder's default line
 * box to the browser-ref's default-font line box (18px @16px, ratio 1.125) and
 * drop the placeholder padding to 0 — but ONLY in composed WPT capture, so every
 * other path (per-component inbox, the 327-pair baseline) stays byte-identical.
 */
class ComposedLineBoxTest {

    // ── Line-height default ─────────────────────────────────────────────────

    @Test
    fun composedMode_pinsRefLineBox_18At16() {
        // The ref default-font line box: 16px font → 18px line-height (1.125).
        assertEquals(18.0f, composedDefaultLineHeightPx(composedWpt = true, fontSizePx = 16f), 0.0001f)
    }

    @Test
    fun nonComposed_keepsNativeDefault_1_2x() {
        // Every non-composed path keeps the historical 1.2× box (19.2 @16px),
        // so the per-component inbox captures and the 327-pair baseline are
        // byte-identical to before this round.
        assertEquals(19.2f, composedDefaultLineHeightPx(composedWpt = false, fontSizePx = 16f), 0.0001f)
    }

    @Test
    fun composedMode_scalesWithFontSize() {
        // The pin is a RATIO so larger placeholder fonts still map to the ref
        // ratio (deferral to an IR-declared line-height happens upstream).
        assertEquals(36.0f, composedDefaultLineHeightPx(composedWpt = true, fontSizePx = 32f), 0.0001f)
    }

    @Test
    fun ratioConstantsAreTheDocumentedValues() {
        assertEquals(1.125f, REF_DEFAULT_FONT_LINE_HEIGHT_RATIO, 0.0f)
        assertEquals(1.2f, NATIVE_DEFAULT_LINE_HEIGHT_RATIO, 0.0f)
    }

    // ── Placeholder padding ─────────────────────────────────────────────────

    @Test
    fun composedMode_dropsPlaceholderPaddingToZero() {
        assertEquals(0, composedPlaceholderTextPaddingDp(composedWpt = true))
    }

    @Test
    fun nonComposed_keeps4dpPlaceholderPadding() {
        assertEquals(4, composedPlaceholderTextPaddingDp(composedWpt = false))
    }
}
