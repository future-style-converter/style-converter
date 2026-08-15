package com.styleconverter.runtime.content

// Wave-42 lane W1 — pin table for PseudoTextMetrics.parityStyle, the
// browser bottom-outs a bucket-channel pseudo run takes in WPT capture so
// it shares one line grid with its sibling item text (and the ::marker,
// whose shared ListMarkerLineBox.resolve it reuses verbatim).

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.core.renderer.WPT_DEFAULT_TEXT_INK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PseudoTextMetricsTest {

    @Test
    fun `outside WPT capture the style passes through untouched`() {
        // The legacy selectors channel (every committed dark-stage
        // baseline) must render byte-identically — identity, same instance.
        val base = TextStyle.Default
        assertSame(base, PseudoTextMetrics.parityStyle(
            base, declaredNormal = false, wptCapture = false, composedWpt = false))
    }

    @Test
    fun `capture bottoms out at the browser defaults`() {
        // The bucket channel carries no typed properties → everything
        // Unspecified → 16sp CanvasText-black at the composed ref's
        // 1.25 line box (20px @16px) — the metrics PlaceholderContent
        // gives the sibling item text.
        val out = PseudoTextMetrics.parityStyle(
            TextStyle(), declaredNormal = false, wptCapture = true, composedWpt = true)
        assertEquals(16f, out.fontSize.value, 0f)
        assertEquals(WPT_DEFAULT_TEXT_INK, out.color)
        assertEquals(20f, out.lineHeight.value, 0.001f)
    }

    @Test
    fun `non-composed capture keeps the native ratio box`() {
        // Per-component inbox path: same 1.2× the item text takes there.
        val out = PseudoTextMetrics.parityStyle(
            TextStyle(), declaredNormal = false, wptCapture = true, composedWpt = false)
        assertEquals(19.2f, out.lineHeight.value, 0.001f)
    }

    @Test
    fun `declared values always beat the bottom-outs`() {
        // Author > UA: a typed declaration (future selectors-channel use)
        // must never be overwritten by a calibration.
        val out = PseudoTextMetrics.parityStyle(
            TextStyle(fontSize = 25.sp, color = Color.Red, lineHeight = 30.sp),
            declaredNormal = false, wptCapture = true, composedWpt = true)
        assertEquals(25f, out.fontSize.value, 0f)
        assertEquals(Color.Red, out.color)
        assertEquals(30f, out.lineHeight.value, 0f)
    }

    @Test
    fun `declared normal keeps the face's natural metrics`() {
        // css-fonts-4 §4.3: a DECLARED `normal` is the CSS answer — stay
        // Unspecified so Compose uses the resolved face's own box.
        val out = PseudoTextMetrics.parityStyle(
            TextStyle(), declaredNormal = true, wptCapture = true, composedWpt = true)
        assertEquals(TextUnit.Unspecified, out.lineHeight)
    }
}
