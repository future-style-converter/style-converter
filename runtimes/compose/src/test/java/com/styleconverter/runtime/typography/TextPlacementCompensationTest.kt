package com.styleconverter.runtime.typography

// Wave-7 text placement fixes — JVM pins for the three renderer-side
// corrections, all pure math so no Android canvas is needed:
//
//  1. LineHeight nested plain-px wire reaches the TextStyle (the
//     extractDp fix, exercised through the full extractTextStyle path —
//     the wire snippet is the LIVE converter output for
//     'line-height: 24px', see ValueExtractorsDpWireTest for the pin).
//  2. centerAlignFractionalDeltaX — cancels StaticLayout ALIGN_CENTER's
//     even-truncation snap (AOSP Layout#getLineStartPos:
//     ((right+left) − ((int)lineMax & ~1)) >> 1).
//  3. subNaturalLineHeightDeltaY — the shared cross-native contract:
//     web paints the glyph band (L − natural)/2 higher when L < natural
//     (css-inline-3 §3.2 negative half-leading); natives clamp, so the
//     renderer translates the run by exactly that delta.

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextPlacementCompensationTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)

    // ── 1. line-height nested-px wire → TextStyle.lineHeight ──

    @Test
    fun `live nested-px line-height wire reaches the TextStyle`() {
        // Before the extractDp fix this came back Unspecified — the 24px
        // line box silently degraded to the font-natural height.
        val style = TextStyleApplier.extractTextStyle(
            listOf(
                IRProperty("FontSize", parse("""{"px":18.0}""")),
                IRProperty("LineHeight", parse("""{"original":{"type":"length","px":24.0}}"""))
            )
        )
        assertNotEquals(TextUnit.Unspecified, style.lineHeight)
        assertEquals(24f.sp, style.lineHeight)
    }

    @Test
    fun `live rem line-height wire resolves through the renderer's resolver pass`() {
        // 'line-height: 2rem' live wire — the RENDERER path: the
        // DynamicValueResolver pre-pass (which owns the relative-unit
        // bases) flattens the nested {v,u} to a top-level px (2 × the
        // 16px harness root default = 32), and extractTextStyle then
        // reads that px. Pins the full chain the device actually runs.
        val resolved = com.styleconverter.runtime.core.variables.DynamicValueResolver.resolve(
            listOf(
                IRProperty(
                    "LineHeight",
                    parse("""{"original":{"type":"length","original":{"v":2.0,"u":"REM"}}}""")
                )
            ),
            variables = emptyMap(),
            ctx = com.styleconverter.runtime.core.variables.DynamicValueResolver.Context()
        )
        val style = TextStyleApplier.extractTextStyle(resolved.properties)
        assertEquals(32f.sp, style.lineHeight)
    }

    // ── 2. center-align even-truncation compensation ──

    @Test
    fun `even-truncation reproduction - odd line width in a 358px box`() {
        // AOSP snap for a 33px line in a 358px box:
        //   start = (358 − (33 & ~1)) / 2 = (358 − 32) / 2 = 163  (integral)
        // True CSS center: (358 − 33) / 2 = 162.5.
        // delta = 162.5 − 163 = −0.5 — the exact web−Android offset the
        // TextAlign_Center pixel measurement showed (web 16.5 vs Android 17).
        val snappedLeft = ((358 - (33 and 1.inv())) / 2).toFloat() // 163f — the AOSP formula
        val delta = TextStyleApplier.centerAlignFractionalDeltaX(
            layoutWidthPx = 358f,
            lineCount = 1,
            lineLeft = { snappedLeft },
            lineRight = { snappedLeft + 33f }
        )
        assertEquals(-0.5f, delta!!, 1e-6f)
    }

    @Test
    fun `even line width snaps to the true center - no compensation`() {
        // 34px line: (358 − 34)/2 = 162 both ways → delta 0 → null (no
        // layer churn for already-correct lines).
        assertNull(
            TextStyleApplier.centerAlignFractionalDeltaX(
                layoutWidthPx = 358f,
                lineCount = 1,
                lineLeft = { 162f },
                lineRight = { 196f }
            )
        )
    }

    @Test
    fun `guard - deltas of 1_5px or more are repositioning not compensation`() {
        // A line laid out 2px off-center is NOT the even-truncation snap
        // (that snap is always sub-pixel) — the helper must refuse.
        assertNull(
            TextStyleApplier.centerAlignFractionalDeltaX(
                layoutWidthPx = 358f,
                lineCount = 1,
                lineLeft = { 160.5f },
                lineRight = { 193.5f }
            )
        )
    }

    @Test
    fun `multi-line compensation follows the widest line`() {
        // Line 0 (widest, 101px, odd): snapped start (358−100)/2 = 129,
        // true center (358−101)/2 = 128.5 → delta −0.5.
        // Line 1 (34px, even): snapped 162 == true center → delta 0.
        // The helper must compute from the WIDEST line (documented
        // approximation — one translation, widest line dominates the mass).
        val lefts = floatArrayOf(129f, 162f)
        val rights = floatArrayOf(230f, 196f)
        val delta = TextStyleApplier.centerAlignFractionalDeltaX(
            layoutWidthPx = 358f,
            lineCount = 2,
            lineLeft = { lefts[it] },
            lineRight = { rights[it] }
        )
        assertEquals(-0.5f, delta!!, 1e-6f)
    }

    @Test
    fun `no lines or blank lines produce no compensation`() {
        // First frame (lineCount 0) and zero-extent lines are no-ops.
        assertNull(
            TextStyleApplier.centerAlignFractionalDeltaX(358f, 0, { 0f }, { 0f })
        )
        assertNull(
            TextStyleApplier.centerAlignFractionalDeltaX(358f, 1, { 179f }, { 179f })
        )
    }

    // ── 3. sub-natural line-height placement compensation ──

    @Test
    fun `shared contract pin - Unitless_1 at 18px Inter`() {
        // L = 18 (line-height: 1 × 18px font), natural = Inter hhea
        // (1984+494)/2048 × 18 = 21.78515625. Web paints the glyph band
        // (18 − 21.785…)/2 = −1.8925… px higher; the compensation must be
        // exactly that (negative == upward translation).
        val natural = (1984f + 494f) / 2048f * 18f
        val delta = TextStyleApplier.subNaturalLineHeightDeltaY(
            resolvedLineHeightPx = 18f,
            naturalLineBoxPx = natural
        )
        assertEquals((18f - natural) / 2f, delta!!, 1e-6f)
        // Sanity on the magnitude the pixel measurement showed (~1.89px up):
        // (18 − 2478/2048×18)/2 = −1.8896484375.
        assertEquals(-1.8896484f, delta, 1e-4f)
    }

    @Test
    fun `at or above natural there is nothing to compensate`() {
        // L == natural: zero half-leading — platform placement already right.
        assertNull(TextStyleApplier.subNaturalLineHeightDeltaY(21.785f, 21.785f))
        // L > natural: POSITIVE half-leading — StaticLayout distributes it
        // correctly (verified across the 9-capture line-height corpus).
        assertNull(TextStyleApplier.subNaturalLineHeightDeltaY(24f, 21.785f))
    }

    @Test
    fun `degenerate metrics are a safe no-op`() {
        // Unset/zero line-height or a first-frame zero layout must never
        // produce a translation.
        assertNull(TextStyleApplier.subNaturalLineHeightDeltaY(0f, 21.785f))
        assertNull(TextStyleApplier.subNaturalLineHeightDeltaY(18f, 0f))
        assertNull(TextStyleApplier.subNaturalLineHeightDeltaY(-1f, 21.785f))
    }
}
