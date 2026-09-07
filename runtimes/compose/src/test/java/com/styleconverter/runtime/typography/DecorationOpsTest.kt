package com.styleconverter.runtime.typography

// DecorationOps pin table — applier campaign wave 21, lane TEXTDECOR
// (B-RC8 decoration style/thickness, B-RC7 unbreakable runs).
//
// TWIN of the iOS suite DecorationOpsTests.swift — SAME cases, SAME
// expected numbers (repo twin rule: identical pin tables). Change one,
// change both.
//
// Every number below is pinned against LIVE wave-21 artifacts:
//   • ref PNGs: tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/
//     white-black-ink-font-lh/css-text-decor/text-decoration-dotted-001
//     .png and -002.png (390×600). Measured red underline bands
//     (thickness 10/20/30 @92px Arial, dots start at x=62):
//       -001 (run width solved W=460): pitches 19.565 / 40.000 / 61.429,
//         band top rows 207 / 363 / 519, text ink bottoms 202 / 353 / 504
//       -002 (W=409): pitches 19.950 / 38.900 / 63.167
//   • per-test IR: tools/titan/runs/wave21-gate/sections/css-text-decor/
//     per-test-ir/wpt__css-text-decor__text-decoration-dotted-00{1,2}
//     .json — TextDecorationStyle "DOTTED", TextDecorationThickness
//     {"type":"length","px":10|20|30}, text "fooשלוםbaz" / "foobarbaz".
//   • Blink algorithm: styled_stroke_data.cc (SelectBestDashGap,
//     DashEffectFromStrokeStyle) + decoration_line_painter.cc
//     (DrawLineAsStroke) — cited per-function in DecorationOps.kt.

import com.styleconverter.runtime.typography.DecorationOps.LineStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecorationOpsTest {

    // ---- SelectBestDashGap: the Chromium dot-rhythm fit ----

    @Test
    fun `dash gap fit reproduces the dotted-001 measured pitches at W=460`() {
        // t=10: ref pitch 19.565 → gap 9.565 (max-count branch wins).
        assertEquals(9.5652174f, DecorationOps.selectBestDashGap(460f, 10f, 10f), 1e-3f)
        // t=20: ref pitch 40.000 exactly → gap 20 (perfect min fit).
        assertEquals(20.0f, DecorationOps.selectBestDashGap(460f, 20f, 20f), 1e-3f)
        // t=30: ref pitch 61.429 → gap 31.429 (min-count branch wins).
        assertEquals(31.428572f, DecorationOps.selectBestDashGap(460f, 30f, 30f), 1e-3f)
    }

    @Test
    fun `dash gap fit reproduces the dotted-002 measured pitches at W=409`() {
        // Same algorithm, second measured run width — all three rows.
        assertEquals(9.95f, DecorationOps.selectBestDashGap(409f, 10f, 10f), 1e-3f)
        assertEquals(18.9f, DecorationOps.selectBestDashGap(409f, 20f, 20f), 1e-3f)
        assertEquals(33.166668f, DecorationOps.selectBestDashGap(409f, 30f, 30f), 1e-3f)
    }

    // ---- dotted: thick (>3px) circle runs ----

    /** Dots of a dotted op list (fails loudly on stray bands). */
    private fun dots(ops: List<DecorationOps.Op>): List<DecorationOps.Op.Dot> =
        ops.map { it as DecorationOps.Op.Dot }

    @Test
    fun `dotted t20 over the dotted-001 run emits 12 flush dots`() {
        val d = dots(DecorationOps.dottedOps(left = 0f, top = 0f, width = 460f, thicknessPx = 20f))
        // Blink: pitch 40−ε over the inset 440px path → 12 dots.
        assertEquals(12, d.size)
        // First dot: center = inset start (t/2), radius t/2, centerY =
        // floor(top + t/2) = 10. Left EDGE = 0 — ref shows dot edges
        // flush with the run start (x=62 in canvas space) for every t.
        assertEquals(10f, d.first().centerX, 1e-3f)
        assertEquals(10f, d.first().centerY, 1e-3f)
        assertEquals(10f, d.first().radius, 1e-3f)
        // Last dot rides the ε-guarantee to the run end: 10 + 11×39.99.
        assertEquals(449.89f, d.last().centerX, 1e-2f)
        // Pitch between consecutive centers = gap + t − ε = 39.99.
        assertEquals(39.99f, d[1].centerX - d[0].centerX, 1e-3f)
    }

    @Test
    fun `dotted t10 and t30 dot counts match the fitted rhythm`() {
        // t=10: pitch 19.555 over 450px inset path → 24 dots.
        assertEquals(24, dots(DecorationOps.dottedOps(0f, 0f, 460f, 10f)).size)
        // t=30: pitch 61.419 over 430px inset path → 8 dots.
        val d30 = dots(DecorationOps.dottedOps(0f, 0f, 460f, 30f))
        assertEquals(8, d30.size)
        // Second dot's left edge = center − 15 ≈ 61.42; canvas-space
        // 62 + 61.42 = 123.42 → ref band -001 t=30 second dot columns
        // 124..152 (AA edges) — the measured pin behind the pitch.
        assertEquals(61.419f, d30[1].centerX - d30[0].centerX, 1e-2f)
    }

    @Test
    fun `dotted band vertical geometry matches the ref rows`() {
        // Feed the REAL ref band tops (rows 207/363/519) and pin the
        // dot centers to the measured band midlines: a t-thick band
        // starting at top spans [top, top+t) → center floor(top + t/2)
        // = 212 / 373 / 534, exactly the ref circles' center rows.
        assertEquals(212f, dots(DecorationOps.dottedOps(62f, 207f, 460f, 10f)).first().centerY, 0f)
        assertEquals(373f, dots(DecorationOps.dottedOps(62f, 363f, 460f, 20f)).first().centerY, 0f)
        assertEquals(534f, dots(DecorationOps.dottedOps(62f, 519f, 460f, 30f)).first().centerY, 0f)
    }

    @Test
    fun `dotted narrower than two dots paints a single dot`() {
        // width < 2t (Blink "just draw 1"): one circle at the inset start.
        val d = dots(DecorationOps.dottedOps(0f, 0f, 30f, 20f))
        assertEquals(1, d.size)
        assertEquals(10f, d.single().centerX, 1e-3f)
    }

    @Test
    fun `dotted at exactly two-dot width paints one dot not zero`() {
        // width == 2t: Blink's `<` keeps the fit path, SelectBestDashGap
        // divides by ZERO gaps → +Inf gap — but Skia still paints the
        // dash interval's phase-0 dot, so ONE dot renders (wave-21
        // skeptic pin: the pre-fix loop's 0·Inf = NaN painted NOTHING).
        val d = dots(DecorationOps.dottedOps(0f, 0f, 20f, 10f))
        assertEquals(1, d.size)
        // Same inset-start center as the narrow single-dot branch.
        assertEquals(5f, d.single().centerX, 1e-3f)
    }

    @Test
    fun `thin dotted (3px and under) paints square dashes not circles`() {
        // Blink StrokeIsDashed: t ≤ 3 → butt-cap t-on/t-off dashes with
        // NO gap refit. t=2, W=20 → dashes at x = 0,4,8,12,16.
        val ops = DecorationOps.dottedOps(0f, 0f, 20f, 2f)
        assertEquals(5, ops.size)
        val first = ops.first() as DecorationOps.Op.Band
        // midY = floor(0 + 1) = 1 (even t, no half shift) → top 0.
        assertEquals(0f, first.top, 0f)
        assertEquals(2f, first.width, 0f)
        assertEquals(2f, first.height, 0f)
        val second = ops[1] as DecorationOps.Op.Band
        assertEquals(4f, second.left, 0f)
    }

    // ---- dashed: Blink's three branches ----

    @Test
    fun `dashed too short for two dashes paints solid`() {
        // t=10 → dash 20; W=35 ≤ 2×20 → Blink nullopt → solid stroke.
        val ops = DecorationOps.dashedOps(0f, 0f, 35f, 10f)
        val band = ops.single() as DecorationOps.Op.Band
        // Stroke centered on midY=5 → band rows [0,10).
        assertEquals(0f, band.top, 0f)
        assertEquals(35f, band.width, 0f)
        assertEquals(10f, band.height, 0f)
    }

    @Test
    fun `dashed two-dash window scales dash and gap proportionally`() {
        // t=10 → dash 20, gap 20; 40 < W=50 ≤ 60 → mult 50/60: two
        // dashes of 16.667 with the second flush at the end.
        val ops = DecorationOps.dashedOps(0f, 0f, 50f, 10f)
        assertEquals(2, ops.size)
        val a = ops[0] as DecorationOps.Op.Band
        val b = ops[1] as DecorationOps.Op.Band
        assertEquals(16.666666f, a.width, 1e-3f)
        // Second dash starts at (dash+gap)·m = 33.333, ends at 50.
        assertEquals(33.333332f, b.left, 1e-3f)
        assertEquals(50f, b.left + b.width, 1e-3f)
    }

    @Test
    fun `dashed long run refits the gap and stays flush`() {
        // t=10 → dash 20, ideal gap 20 over W=460: refit keeps gap 20
        // (perfect fit) → dashes at 0,40,…,440 — twelve of width 20.
        val ops = DecorationOps.dashedOps(0f, 0f, 460f, 10f)
        assertEquals(12, ops.size)
        val last = ops.last() as DecorationOps.Op.Band
        assertEquals(440f, last.left, 1e-3f)
        assertEquals(20f, last.width, 1e-3f)
        // Flush pin: at W=450 the REFIT gap (19.09) spreads 12 dashes so
        // the last one ends exactly at the run end — SelectBestDashGap
        // always lands the final dash flush (the min() clip in the
        // emitter only fires for the thin-dotted FIXED-gap pattern).
        val refit = DecorationOps.dashedOps(0f, 0f, 450f, 10f)
        val refitLast = refit.last() as DecorationOps.Op.Band
        assertEquals(450f, refitLast.left + refitLast.width, 1e-2f)
    }

    // ---- solid identity + honest double/wavy fallback ----

    @Test
    fun `solid emits the band itself byte-for-byte`() {
        // The legacy draw call — auto-path captures cannot move.
        val ops = DecorationOps.styleOps(20f, 43f, 212f, 2f, LineStyle.SOLID)
        assertEquals(listOf<DecorationOps.Op>(DecorationOps.Op.Band(20f, 43f, 212f, 2f)), ops)
    }

    @Test
    fun `double and wavy fall back to solid and are flagged lossy`() {
        // Fallback is the same solid band…
        assertEquals(
            listOf<DecorationOps.Op>(DecorationOps.Op.Band(0f, 0f, 100f, 2f)),
            DecorationOps.styleOps(0f, 0f, 100f, 2f, LineStyle.DOUBLE)
        )
        // …and the caller-visible lossy flag names exactly those two.
        assertTrue(DecorationOps.isLossyFallback(LineStyle.DOUBLE))
        assertTrue(DecorationOps.isLossyFallback(LineStyle.WAVY))
        assertFalse(DecorationOps.isLossyFallback(LineStyle.SOLID))
        assertFalse(DecorationOps.isLossyFallback(LineStyle.DOTTED))
        assertFalse(DecorationOps.isLossyFallback(LineStyle.DASHED))
    }

    // ---- explicit-thickness placement (css-text-decor-4 §2.4) ----

    @Test
    fun `explicit underline gap is Blink's ceil-half rule`() {
        // Ref-pinned: band top − ink bottom = 5/10/15 for T=10/20/30.
        assertEquals(5f, DecorationOps.explicitUnderlineGapPx(10f), 0f)
        assertEquals(10f, DecorationOps.explicitUnderlineGapPx(20f), 0f)
        assertEquals(15f, DecorationOps.explicitUnderlineGapPx(30f), 0f)
        // Thin floors: max(1, ceil(T/2)).
        assertEquals(1f, DecorationOps.explicitUnderlineGapPx(1f), 0f)
        assertEquals(1f, DecorationOps.explicitUnderlineGapPx(2f), 0f)
        assertEquals(2f, DecorationOps.explicitUnderlineGapPx(3f), 0f)
    }

    @Test
    fun `explicit underline bands land on the ref band tops`() {
        // Ref -001: text ink bottoms (snapped baselines) 202/353/504 →
        // band tops 207/363/519 for T = 10/20/30 at 92px.
        for ((baseline, t, expectedTop) in listOf(
            Triple(202f, 10f, 207f), Triple(353f, 20f, 363f), Triple(504f, 30f, 519f)
        )) {
            val band = DecorationOps.explicitBands(
                lineCount = 1, fontSizePx = 92f, thicknessPx = t,
                underline = true, overline = false, lineThrough = false,
                lineBaseline = { baseline }, lineLeft = { 62f }, lineRight = { 522f }
            ).single()
            assertEquals(expectedTop, band.top, 0f)
            // Band carries the EXPLICIT thickness (the B-RC8 fix) and
            // the line's inked extent.
            assertEquals(t, band.thickness, 0f)
            assertEquals(460f, band.width, 0f)
        }
    }

    @Test
    fun `explicit overline and strike keep the auto anchors with T swapped`() {
        val bands = DecorationOps.explicitBands(
            lineCount = 1, fontSizePx = 92f, thicknessPx = 10f,
            underline = false, overline = true, lineThrough = true,
            lineBaseline = { 202f }, lineLeft = { 0f }, lineRight = { 100f }
        )
        // Overline: bottom flush with baseline − round(92·1984/2048)=89
        // → top = 202 − 89 − 10 = 103.
        assertEquals(103f, bands[0].top, 0f)
        // Strike: center at 202 − 92·671/2048 = 171.86; top =
        // round(171.86 − 5) = 167 (center-anchored, T-independent mid).
        assertEquals(167f, bands[1].top, 0f)
    }

    @Test
    fun `explicit T=2 intentionally diverges from the auto gap==T rule`() {
        // AUTO at 22px pins top = baseline + 2 (wave-5 oracle, rows
        // 43-44, TypographyTextPipelineFixTest). An EXPLICIT 2px
        // thickness uses Blink's gap = max(1, ceil(2/2)) = 1 → top 42.
        // Documented seam: the auto path keeps the capture-pinned rows.
        val band = DecorationOps.explicitBands(
            lineCount = 1, fontSizePx = 22f, thicknessPx = 2f,
            underline = true, overline = false, lineThrough = false,
            lineBaseline = { 41f }, lineLeft = { 20f }, lineRight = { 232f }
        ).single()
        assertEquals(42f, band.top, 0f)
    }

    @Test
    fun `explicit bands skip empty visual lines and nothing-flagged calls`() {
        // Zero inked extent → no band (browser paints nothing).
        assertTrue(DecorationOps.explicitBands(
            1, 92f, 10f, underline = true, overline = false, lineThrough = false,
            lineBaseline = { 100f }, lineLeft = { 50f }, lineRight = { 50f }
        ).isEmpty())
        // No flags → total no-op.
        assertTrue(DecorationOps.explicitBands(
            1, 92f, 10f, underline = false, overline = false, lineThrough = false,
            lineBaseline = { 100f }, lineLeft = { 0f }, lineRight = { 100f }
        ).isEmpty())
    }

    // ---- B-RC7: unbreakable-run detection (UAX #14 approximation) ----

    @Test
    fun `the dotted test strings are unbreakable`() {
        // The LIVE IR texts: no spaces, no break punctuation, Hebrew is
        // not ideographic → the ref keeps each on ONE overflowing line.
        assertFalse(DecorationOps.hasSoftWrapOpportunity("fooשלוםbaz"))
        assertFalse(DecorationOps.hasSoftWrapOpportunity("foobarbaz"))
        // Empty string: nothing to wrap.
        assertFalse(DecorationOps.hasSoftWrapOpportunity(""))
        // NBSP FORBIDS breaks (css-text-3 §5.5 / UAX #14 GL) — Kotlin's
        // isWhitespace counts it as SPACE_SEPARATOR, so the predicate
        // carves it out explicitly (the Swift twin does the same).
        assertFalse(DecorationOps.hasSoftWrapOpportunity("foo\u00A0bar"))
    }

    @Test
    fun `spaces hyphens soft-hyphens and ideographs are wrap opportunities`() {
        // Plain space (UAX #14 SP).
        assertTrue(DecorationOps.hasSoftWrapOpportunity("foo bar"))
        // Break-after hyphen (class HY).
        assertTrue(DecorationOps.hasSoftWrapOpportunity("foo-bar"))
        // Soft hyphen + zero-width space (explicit opportunities).
        assertTrue(DecorationOps.hasSoftWrapOpportunity("foo\u00ADbar"))
        assertTrue(DecorationOps.hasSoftWrapOpportunity("foo\u200Bbar"))
        // Ideographic run (class ID — breaks between any two).
        assertTrue(DecorationOps.hasSoftWrapOpportunity("日本語"))
        // Em dash (class BA).
        assertTrue(DecorationOps.hasSoftWrapOpportunity("foo\u2014bar"))
        // NEL U+0085 (class BK, mandatory break): Java's whitespace
        // sets exclude it while Swift's White_Space includes it; the
        // predicate carves it IN so the twins agree (skeptic pin).
        assertTrue(DecorationOps.hasSoftWrapOpportunity("foo\u0085bar"))
    }
}
