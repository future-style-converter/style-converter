package com.styleconverter.runtime.typography

// Wave-7 text placement fixes — JVM pins for the three renderer-side
// corrections, all pure math so no Android canvas is needed:
//
//  1. LineHeight nested plain-px wire reaches the TextStyle (the
//     extractDp fix, exercised through the full extractTextStyle path —
//     the wire snippet is the LIVE converter output for
//     'line-height: 24px', see ValueExtractorsDpWireTest for the pin).
//  2. centerAlignFractionalDeltaX — cancels StaticLayout ALIGN_CENTER's
//     DRAW-path even-truncation snap (AOSP Layout#getLineStartPos:
//     ((left+right) − ((int)lineMax & ~1)) >> 1), reworked in wave 8 to
//     model the draw path from the UNROUNDED advance after the wave-7
//     version was fooled by the self-consistently ROUNDED report path
//     (getLineLeft/getLineRight floor/ceil in their ALIGN_CENTER branch).
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

    // ── 2. center-align even-truncation compensation (DRAW-path model) ──

    @Test
    fun `AOSP even-truncation reproduction - the TextAlign_Center fixture numbers`() {
        // The wave-8 adjudicated fixture case: advance ≈ 219.3px (raw
        // Paint extent, fractional) centered in a 252px layout box.
        //   draw:  (252 − ((int)219.3 & ~1)) >> 1 = (252 − 218) >> 1 = 17
        //   ideal: (252 − 219.3) / 2 = 16.35
        //   delta: 16.35 − 17 = −0.65 (browser draws LEFT of Android).
        // Wave-7's report-path version computed 0 on these numbers (left
        // floor(16.35)=16, right ceil'd → width 220 → (252−220)/2−16 = 0)
        // — pinning the fixture here is the regression test for that trap.
        val delta = TextStyleApplier.centerAlignFractionalDeltaX(
            layoutWidthPx = 252f,
            lineCount = 1,
            lineAdvance = { 219.3f }
        )
        // Expected from the model, written out so the pin is self-checking:
        val expected = (252f - 219.3f) / 2f - ((252 - (219 and 1.inv())) shr 1)
        assertEquals(expected, delta!!, 1e-5f)
        assertEquals(-0.65f, delta, 1e-4f)
    }

    @Test
    fun `odd integral advance still snaps one pixel wide of center`() {
        // 33px advance in a 358px box (the wave-7 arithmetic, still valid
        // under the draw model because an INTEGER odd advance truncates
        // to 32 the same way):
        //   draw (358 − 32) >> 1 = 163; ideal (358 − 33)/2 = 162.5 → −0.5.
        val delta = TextStyleApplier.centerAlignFractionalDeltaX(
            layoutWidthPx = 358f,
            lineCount = 1,
            lineAdvance = { 33f }
        )
        assertEquals(-0.5f, delta!!, 1e-6f)
    }

    @Test
    fun `even integral advance draws at the true center - no compensation`() {
        // 34px advance: draw (358 − 34) >> 1 = 162 == ideal (358 − 34)/2
        // → delta 0 → null (no layer churn for already-correct lines).
        assertNull(
            TextStyleApplier.centerAlignFractionalDeltaX(
                layoutWidthPx = 358f,
                lineCount = 1,
                lineAdvance = { 34f }
            )
        )
    }

    @Test
    fun `guard - model deltas stay sub-pixel across the advance range`() {
        // The draw-model delta is bounded: truncate-to-even loses < 2px of
        // advance (halved → < 1px) and the integral >> 1 loses at most
        // another 0.5px the OTHER way — so |delta| < 1.5 always holds for
        // model-consistent inputs and the 1.5px guard can only trip on
        // inputs that violate the model (RTL/justified lines). Sweep a
        // fractional advance across the box to pin both facts: every
        // result is null or sub-1.5px.
        var advance = 0.1f
        while (advance < 357f) {
            val d = TextStyleApplier.centerAlignFractionalDeltaX(
                layoutWidthPx = 358f,
                lineCount = 1,
                lineAdvance = { advance }
            )
            if (d != null) {
                org.junit.Assert.assertTrue(
                    "delta $d out of guard range for advance $advance",
                    kotlin.math.abs(d) < 1.5f
                )
            }
            advance += 0.7f
        }
    }

    @Test
    fun `multi-line compensation follows the widest line`() {
        // Line 0 (widest, advance 101.5): draw (358 − 100) >> 1 = 129,
        // ideal (358 − 101.5)/2 = 128.25 → delta −0.75.
        // Line 1 (advance 34, even): draw == ideal → would be 0.
        // The helper must compute from the WIDEST line (documented
        // approximation — one translation, widest line dominates the mass).
        val advances = floatArrayOf(101.5f, 34f)
        val delta = TextStyleApplier.centerAlignFractionalDeltaX(
            layoutWidthPx = 358f,
            lineCount = 2,
            lineAdvance = { advances[it] }
        )
        assertEquals(-0.75f, delta!!, 1e-6f)
    }

    @Test
    fun `no lines or blank lines produce no compensation`() {
        // First frame (lineCount 0) and zero-advance lines are no-ops.
        assertNull(
            TextStyleApplier.centerAlignFractionalDeltaX(358f, 0) { 0f }
        )
        assertNull(
            TextStyleApplier.centerAlignFractionalDeltaX(358f, 1) { 0f }
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
