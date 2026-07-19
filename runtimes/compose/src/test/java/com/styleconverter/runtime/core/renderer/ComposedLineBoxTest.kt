package com.styleconverter.runtime.core.renderer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure composed-WPT text line-box decisions (WptCaptureMode.kt,
 * FIX 2 of TITAN Round 4b, recalibrated at the corpus-v4.1 LINE-HEIGHT
 * sub-boundary): [composedDefaultLineHeightPx] and
 * [composedPlaceholderTextPaddingDp]. These pin the placeholder's default line
 * box to the browser-ref's PINNED line box (20px @16px, ratio 1.25 — the ref
 * injection's explicit REF_LINE_HEIGHT, no longer any font's `normal` metrics)
 * and drop the placeholder padding to 0 — but ONLY in composed WPT capture, so
 * every other path (per-component inbox, the 327-pair baseline) stays
 * byte-identical.
 */
class ComposedLineBoxTest {

    // ── Line-height default ─────────────────────────────────────────────────

    @Test
    fun composedMode_pinsRefLineBox_20At16() {
        // The corpus-v4.1 pinned ref line box: 16px font → 20px line-height
        // (1.25 — the ref injection's explicit REF_LINE_HEIGHT).
        assertEquals(20.0f, composedDefaultLineHeightPx(composedWpt = true, fontSizePx = 16f), 0.0001f)
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
        // ratio (deferral to an IR-declared line-height happens upstream) —
        // matching the ref's inherited UNITLESS 1.25, which recomputes
        // against each descendant's own font-size (32px → 40px).
        assertEquals(40.0f, composedDefaultLineHeightPx(composedWpt = true, fontSizePx = 32f), 0.0001f)
    }

    @Test
    fun ratioConstantsAreTheDocumentedValues() {
        // 1.25 == capture-browser-ref.mjs REF_LINE_HEIGHT (the corpus-v4.1
        // deterministic pin, all four surfaces in lock-step); 1.2 == the
        // untouched dark-stage native default.
        assertEquals(1.25f, REF_DEFAULT_FONT_LINE_HEIGHT_RATIO, 0.0f)
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
