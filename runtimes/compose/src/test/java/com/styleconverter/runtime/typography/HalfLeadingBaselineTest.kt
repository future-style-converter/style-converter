package com.styleconverter.runtime.typography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.floor

/**
 * JVM pins for [HalfLeadingBaseline] — wave 39, lane A1.
 *
 * The whole object exists because two engines round the SAME face ascent in
 * opposite directions at a .5 tie, so these pins are deliberately written
 * about the ROUNDING DIRECTION first and the derived pixel second: a future
 * "simplification" that swaps `floor(a + 0.5)` for `Math.round` on the
 * negative scalar, or that rounds the content area instead of its two halves,
 * must fail here rather than quietly re-introduce the 1px glyph-run lift.
 */
class HalfLeadingBaselineTest {

    // ── the tie, stated twice: Blink's direction and Android's ──────────────

    /** Blink: `SkScalarRoundToScalar(-fAscent)` on the POSITIVE magnitude. */
    @Test
    fun `blinkRound sends a half-integer ascent UP`() {
        // Inter @16px: 1984/2048 × 16 = 15.5 exactly — the tie that splits.
        assertEquals(16, HalfLeadingBaseline.blinkRound(15.5f))
        // …and the same rule at the other tie sizes (fontSize ≡ 16 mod 32).
        assertEquals(47, HalfLeadingBaseline.blinkRound(46.5f))  // 48px
        assertEquals(78, HalfLeadingBaseline.blinkRound(77.5f))  // 80px
    }

    /**
     * `Paint.FontMetricsInt.ascent` is `SkScalarRoundToInt` applied to the
     * NEGATIVE scalar, so the magnitude comes out `ceil(a − 0.5)` — the
     * contrast that IS the defect. Spelled as arithmetic (not as a call into
     * the object) because the object deliberately does not model this side;
     * the renderer measures it off the live TextLayoutResult instead.
     */
    @Test
    fun `the platform rounds the same half-integer ascent DOWN`() {
        val ascent = 15.5f
        // Skia's round on the negative scalar: floor(-15.5 + 0.5) = -15.
        assertEquals(-15, floor(-ascent + 0.5f).toInt())
        // …which is the magnitude `ceil(a - 0.5)`, 1 BELOW Blink's 16.
        assertEquals(15, ceil(ascent - 0.5f).toInt())
        assertEquals(1, HalfLeadingBaseline.blinkRound(ascent) - ceil(ascent - 0.5f).toInt())
    }

    /** Away from the tie the two rules agree — which is why only some font
     *  sizes are affected, and why a blanket +1 would be wrong. */
    @Test
    fun `off-tie ascents round identically on both engines`() {
        for (fontSizePx in intArrayOf(8, 12, 14, 18, 20, 22, 24, 32)) {
            val a = HalfLeadingBaseline.ASCENT_EM * fontSizePx
            assertEquals(
                "fontSize=$fontSizePx",
                HalfLeadingBaseline.blinkRound(a),
                ceil(a - 0.5f).toInt()
            )
        }
    }

    // ── the face constants ─────────────────────────────────────────────────

    /** One face, one number: drift against the decoration geometry's copy of
     *  Inter's hhea ascender is a bug in whichever file moved. */
    @Test
    fun `ascent em matches the decoration geometry constant`() {
        assertEquals(DecorationOps.ASCENT_EM, HalfLeadingBaseline.ASCENT_EM, 0f)
    }

    /** …and the two halves must still sum to the content ratio the iOS twin
     *  (`LineBoxMetrics.interContentRatio`) uses: (1984 + 494)/2048. */
    @Test
    fun `ascent plus descent em is Inter's content ratio`() {
        assertEquals(
            2478f / 2048f,
            HalfLeadingBaseline.ASCENT_EM + HalfLeadingBaseline.DESCENT_EM,
            1e-6f
        )
    }

    // ── the Blink baseline model (CSS 2.1 §10.8.1) ─────────────────────────

    /**
     * The corpus's own case: 16px Inter in the ref's pinned `line-height:
     * 1.25` box. Rounded ascent 16 + rounded descent 4 == the 20px box
     * exactly, so the leading is zero and the baseline IS the ascent.
     */
    @Test
    fun `ref baseline at the corpus default is 16px below the line top`() {
        assertEquals(16f, HalfLeadingBaseline.refBaselineFromLineTopPx(16f, 20f)!!, 0f)
    }

    /** A positive leading is split in half and the ascent half FLOORED —
     *  22px Inter (ascent 21.3125 → 21, descent 5.3086 → 5, content 26) in a
     *  27.5px box: floor(21 + 0.75) = 21, not 22. */
    @Test
    fun `positive half-leading floors toward the line-box top`() {
        assertEquals(21f, HalfLeadingBaseline.refBaselineFromLineTopPx(22f, 27.5f)!!, 0f)
    }

    /** Half-leading is SIGNED (§10.8.1): a line box BELOW the content area
     *  lifts the baseline above the ascent instead of clamping. 16px Inter
     *  (content 20) in a 16px box → floor(16 + (16−20)/2) = 14. */
    @Test
    fun `negative half-leading lifts the baseline`() {
        assertEquals(14f, HalfLeadingBaseline.refBaselineFromLineTopPx(16f, 16f)!!, 0f)
    }

    /** Degenerate inputs have no CSS box to reason about — `line-height:
     *  normal` reaches here as a non-positive box and must not invent one. */
    @Test
    fun `no font size or no line box yields no model`() {
        assertNull(HalfLeadingBaseline.refBaselineFromLineTopPx(0f, 20f))
        assertNull(HalfLeadingBaseline.refBaselineFromLineTopPx(16f, 0f))
        assertNull(HalfLeadingBaseline.refBaselineFromLineTopPx(16f, -1f))
        assertNull(HalfLeadingBaseline.refBaselineFromLineTopPx(Float.NaN, 20f))
    }

    // ── the correction the renderer applies ────────────────────────────────

    /**
     * THE MEASURED DEFECT. Compose lays the 16px/20px line out with its
     * baseline 15px below the line-box top (ascent 15, odd leading pixel
     * given to the bottom); Chromium puts it at 16. The glyph run must move
     * DOWN one whole pixel — the +1 that flips css-ui box-sizing-010/011/
     * 014..019 from F to P.
     */
    @Test
    fun `deltaY moves the corpus-default run down exactly one pixel`() {
        assertEquals(1f, HalfLeadingBaseline.deltaY(16f, 20f, 15f), 0f)
    }

    /** The correction is SIGNED, not a constant nudge: where the platform
     *  lands BELOW Blink (22px/27.5px — Compose 22, Blink 21) it moves up. */
    @Test
    fun `deltaY moves a run up when the platform sits too low`() {
        assertEquals(-1f, HalfLeadingBaseline.deltaY(22f, 27.5f, 22f), 0f)
    }

    /** Agreement must cost nothing — no layer translation, no resampling,
     *  byte-identical output for every run that was already right. */
    @Test
    fun `deltaY is zero when the platform already agrees`() {
        assertEquals(0f, HalfLeadingBaseline.deltaY(16f, 20f, 16f), 0f)
        assertEquals(0f, HalfLeadingBaseline.deltaY(24f, 30f, 23f), 0f)
    }

    /**
     * A rounding disagreement is ±1px by construction. Anything at or beyond
     * [HalfLeadingBaseline.MAX_ABS_DELTA_PX] is a DIFFERENT defect (wrong
     * line box, unrelated fallback face, pre-settle layout) and must be left
     * visible rather than translated out of sight.
     */
    @Test
    fun `deltaY refuses a correction larger than a rounding step`() {
        assertEquals(0f, HalfLeadingBaseline.deltaY(16f, 20f, 13f), 0f)   // −3
        assertEquals(0f, HalfLeadingBaseline.deltaY(16f, 20f, 14f), 0f)   // −2, the boundary
        assertEquals(1f, HalfLeadingBaseline.deltaY(16f, 20f, 15f), 0f)   // −1, still in
    }

    /** Pre-settle / empty layouts report a non-positive baseline: there is
     *  no glyph run on screen yet, so there is nothing to move. */
    @Test
    fun `deltaY is zero before the layout settles`() {
        assertEquals(0f, HalfLeadingBaseline.deltaY(16f, 20f, 0f), 0f)
        assertEquals(0f, HalfLeadingBaseline.deltaY(16f, 20f, -4f), 0f)
        assertEquals(0f, HalfLeadingBaseline.deltaY(16f, 20f, Float.NaN), 0f)
    }

    /** `line-height: normal` (no CSS box) reaches deltaY as a zero box and
     *  must keep the face's own metrics — the same gate composedLineBoxSnap
     *  applies with `refLineBoxPx > 0f`. */
    @Test
    fun `deltaY is zero for a line-height-normal run`() {
        assertEquals(0f, HalfLeadingBaseline.deltaY(16f, 0f, 15f), 0f)
    }

    /**
     * The correction is always a WHOLE pixel for a whole-pixel platform
     * baseline: `refBaselineFromLineTopPx` floors, so no fractional
     * translation can reach `graphicsLayer` and blur the glyph raster.
     */
    @Test
    fun `deltaY is integral across the corpus font-size range`() {
        for (fontSizePx in 8..48) {
            val fs = fontSizePx.toFloat()
            val lineBox = fs * 1.25f                       // the ref's pinned ratio
            val ref = HalfLeadingBaseline.refBaselineFromLineTopPx(fs, lineBox)!!
            assertEquals("fontSize=$fontSizePx", ref, floor(ref), 0f)
            // A platform baseline one below the model always yields +1.
            assertEquals(
                "fontSize=$fontSizePx",
                1f,
                HalfLeadingBaseline.deltaY(fs, lineBox, ref - 1f),
                0f
            )
        }
    }
}
