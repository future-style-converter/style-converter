package com.styleconverter.runtime.typography

import kotlin.math.abs
import kotlin.math.floor

/**
 * WHERE THE BASELINE SITS INSIDE A LINE BOX — CSS 2.1 §10.8.1 half-leading,
 * and the ONE rounding asymmetry that makes Compose disagree with Chromium
 * about it by exactly 1px (wave 39, lane A1).
 *
 * ## The defect, measured on the frozen wave38-final corpus
 * Per-row ink profile of `wpt/css-ui/box-sizing-010` (two wrapped lines of a
 * default-styled `<p>`, then a 70px green box), Android composed capture vs
 * the frozen browser ref:
 *
 * | y  | ref | Android | iOS |
 * |----|-----|---------|-----|
 * | 37 |  47 |      24 |  38 |
 * | 38 |  28 | **204** |  24 |
 * | 39 | 224 |     234 | 209 |
 * | 47 | 216 |  **55** | 201 |
 * | 48 |  52 |      11 |  45 |
 * | 88 | box |     box | box |
 *
 * The whole glyph run — BOTH lines, so it is a constant offset and not a
 * per-line drift — sits exactly 1px above the ref, while the green box at
 * y=88 lands on the same row on all three platforms (so the BLOCK flow, the
 * line-box HEIGHT and the paragraph ADVANCE are all already correct; only
 * the ink inside the line box is misplaced). iOS is 0px off.
 *
 * Blast radius, same corpus: of 906 scored Android tests, 545 have their
 * row-ink profile best-correlated at a +1px (downward) shift; restricted to
 * tests that render text at all it is 541/697, and restricted to tests that
 * render NO text it is 4/201 — i.e. this is a TEXT-placement defect, not a
 * layout one.
 *
 * ## The arithmetic (why exactly 1px, and only at some font sizes)
 * Both engines compute the same thing — "centre the font's content area
 * (ascent+descent) in an L-tall line box" — but they round the raw face
 * ascent in OPPOSITE directions at a .5 tie:
 *
 *  * Blink rounds the POSITIVE magnitude: `SkScalarRoundToScalar(-fAscent)`
 *    = `floor(a + 0.5)` (blink::SimpleFontData::PlatformInit), then splits
 *    the leading and FLOORS the result — `FontHeight::AddLeading`:
 *    `ascent = (ascent + (L − (ascent + descent)) / 2).Floor()`.
 *  * `android.graphics.Paint.FontMetricsInt.ascent` is the SAME Skia
 *    rounding applied to the NEGATIVE scalar — `SkScalarRoundToInt(fAscent)`
 *    = `floor(fAscent + 0.5)` — so the magnitude comes out `ceil(a − 0.5)`.
 *
 * `floor(a + 0.5)` and `ceil(a − 0.5)` agree everywhere EXCEPT when `a` has
 * fractional part exactly ½, where they differ by 1. Inter's hhea ascender
 * is 1984/2048 em, so `a = 0.96875 × fontSize` is a half-integer exactly at
 * `fontSize ≡ 16 (mod 32)` — and 16px is the corpus's inherited body size.
 * Worked at 16px, L = 20 (the ref's pinned `line-height: 1.25`):
 *
 *     Blink   : ascent = floor(15.5 + .5) = 16, descent = 4, content = 20,
 *               halfLeading = 0, baseline = floor(16 + 0) = 16
 *     Android : ascent = −floor(−15.5 + .5) = 15, descent = 4, origin = 19,
 *               LineHeightStyleSpan gives the odd leading px to the BOTTOM,
 *               baseline = 15
 *
 * 16 − 15 = the measured 1px. iOS never had it: `LineBoxMetrics` keeps the
 * UNROUNDED 19.359375px content height and splits the leading in floats, so
 * its baseline lands at 15.82 — the same device row as Chromium's 16.
 *
 * ## What this object does NOT do
 * It models only the BLINK side. The Android side is READ from the live
 * `TextLayoutResult` (`getLineBaseline(0) − getLineTop(0)`) at the call
 * site, so no assumption about Compose's `LineHeightStyleSpan` leading split
 * can silently rot. The correction is a pure DRAW-time translation — layout,
 * box heights and every committed capture's component geometry are
 * untouched — and the caller gates it to composed-WPT capture only.
 *
 * Pure + JVM-pinned (`HalfLeadingBaselineTest`).
 */
object HalfLeadingBaseline {

    /** Inter hhea.ascender / unitsPerEm = 1984/2048 — the same face constant
     *  `DecorationOps.ASCENT_EM` and `TextStyleApplier.DECORATION_ASCENT_EM`
     *  already use for the decoration geometry. One face, one number. */
    const val ASCENT_EM: Float = 1984f / 2048f

    /** Inter hhea.descender magnitude / unitsPerEm = 494/2048. Together with
     *  [ASCENT_EM] this is the (1984+494)/2048 content ratio the iOS twin
     *  spells as `LineBoxMetrics.interContentRatio`, split into the two
     *  halves Blink rounds SEPARATELY (that separate rounding is the point —
     *  rounding the sum instead would lose the tie this object exists for). */
    const val DESCENT_EM: Float = 494f / 2048f

    /**
     * The biggest correction this object will hand back. A half-leading
     * rounding disagreement is ±1px by construction (both sides are integers
     * derived from the same face at the same size), so anything at or beyond
     * 2px is a DIFFERENT defect — a wrong line box, a fallback face with
     * unrelated metrics, a pre-settle layout — and silently translating the
     * glyph run by it would paper over that defect instead of reporting it.
     */
    const val MAX_ABS_DELTA_PX: Float = 2f

    /**
     * Blink's `SkScalarRoundToScalar` — round half UP on the value as given.
     * Named rather than inlined because the whole defect is which side of a
     * .5 tie each engine lands on; a reader must be able to see the rule.
     */
    fun blinkRound(v: Float): Int = floor(v + 0.5f).toInt()

    /**
     * Chromium's baseline offset from the LINE BOX TOP, in px.
     *
     * `FontHeight::AddLeading` (blink/renderer/platform/fonts/font_height.h):
     * half-leading is `(L − (ascent + descent)) / 2` — SIGNED, so a line
     * box below the content area legitimately returns a baseline above the
     * ascent — and the sum is floored to a whole layout pixel.
     *
     * @param fontSizePx the used `font-size` in px.
     * @param lineHeightPx the used `line-height` in px (the CSS line box).
     * @return the integral baseline offset, or null for degenerate inputs
     *   (no font size, or no declared/calibrated box — `line-height: normal`
     *   has no leading to split, the face's own metrics ARE the answer).
     */
    fun refBaselineFromLineTopPx(fontSizePx: Float, lineHeightPx: Float): Float? {
        if (!fontSizePx.isFinite() || fontSizePx <= 0f) return null
        if (!lineHeightPx.isFinite() || lineHeightPx <= 0f) return null
        // Blink rounds ascent and descent INDEPENDENTLY at face-load time…
        val ascent = blinkRound(ASCENT_EM * fontSizePx)
        val descent = blinkRound(DESCENT_EM * fontSizePx)
        // …and the content area is the sum of those two rounded numbers.
        val content = (ascent + descent).toFloat()
        // §10.8.1: leading = L − content area, split evenly above and below.
        val halfLeading = (lineHeightPx - content) / 2f
        // AddLeading's `.Floor()` — the ascent half is truncated toward the
        // line-box top, and the descent half absorbs the remainder.
        return floor(ascent + halfLeading)
    }

    /**
     * The signed DOWNWARD translation (px) the glyph run needs so its
     * baseline lands where Chromium puts it. Positive = move down.
     *
     * @param fontSizePx used `font-size` in px.
     * @param lineHeightPx used `line-height` in px; non-positive means
     *   `normal` (no CSS box) and yields 0 — nothing to correct.
     * @param platformBaselineFromLineTopPx what Compose ACTUALLY laid out:
     *   `TextLayoutResult.getLineBaseline(0) − getLineTop(0)`. Measured, not
     *   modelled, so this stays correct if Compose changes its leading split.
     * @return 0 whenever the correction is unavailable, already exact, or
     *   larger than [MAX_ABS_DELTA_PX] (see that constant for why a big
     *   delta must NOT be applied).
     */
    fun deltaY(
        fontSizePx: Float,
        lineHeightPx: Float,
        platformBaselineFromLineTopPx: Float
    ): Float {
        val ref = refBaselineFromLineTopPx(fontSizePx, lineHeightPx) ?: return 0f
        // A non-positive measured baseline is a pre-settle / empty layout —
        // there is no glyph run on screen yet to move.
        if (!platformBaselineFromLineTopPx.isFinite() ||
            platformBaselineFromLineTopPx <= 0f
        ) return 0f
        val delta = ref - platformBaselineFromLineTopPx
        if (!delta.isFinite() || abs(delta) >= MAX_ABS_DELTA_PX) return 0f
        return delta
    }
}
