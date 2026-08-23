package com.styleconverter.runtime.typography

import kotlin.math.ceil

/**
 * WHERE EACH LINE OF A MULTI-LINE RUN SITS ON THE FRACTIONAL CSS LINE GRID —
 * wave 46, lane Y5. Compose-only; the iOS line box already stacks in floats.
 *
 * ## The per-line rounding, located in the platform (compose-ui-text 1.11.4)
 * `androidx.compose.ui.text.android.style.LineHeightStyleSpan
 * .calculateTargetMetrics` (bytecode-verified, `ui-text-android` aar):
 *
 *     ceiledLineHeight = ceil(lineHeight)          // 31.25 → 32, EVERY line
 *     descent += ceil(diff × (1 − topRatio))       // Center → the odd px goes below
 *     ascent   = descent − ceiledLineHeight        // line = exactly ceil(L)
 *
 * `StaticLayout.out()` then advances `v += descent − ascent` in whole pixels
 * per line, so an n-line paragraph at a 31.25px CSS box is laid out 32n tall
 * with line i at 32i. Chromium (LayoutNG) keeps the line-box edges in
 * LayoutUnit (1/64 px): line i's top is the EXACT i × L and only the glyph
 * ink is snapped to a device row at paint time — so its ink rows start at
 * round(i × L): 0, 31, 63, 94, 125, … (pitch 31, 32, 31, 31 — measured on
 * the frozen css-counter-styles refs). Ten lines at 25px advance 312.5px in
 * the browser and 320px in Compose; `composedLineBoxSnap` (ComponentRenderer)
 * already REPORTS the browser's round(n × L) = 313 for the box, but it
 * centres the 320px block inside it, so line 0 draws 3px high and line 9
 * 4px low. This object decides, per line, the whole-pixel translation that
 * puts line i's ink where Chromium's accumulated edge puts it.
 *
 * ## The rule
 * Inside a snapped box whose natural Compose block is placed at
 * [placementYPx] (≤ 0, the snap's symmetric trim), line i must land at
 * `round(prefix + i·L) − round(prefix)` from the box top — the wave-30
 * accumulated-position rounding (`ListMarkerLineBox.snappedHeightPx`), so
 * the bottom of line n−1 is exactly the box the snap reports. Compose put it
 * at `placementY + (top_i − top_0)`. The translation is the difference, and
 * because both terms are integers it never resamples glyph ink.
 *
 * ## What is deliberately NOT modelled
 * - The half-leading of line 0 (which device row the FIRST baseline takes)
 *   stays with [HalfLeadingBaseline] / FIX 4: this plan moves line 0 by
 *   exactly `−placementY`, i.e. back to the box top, and every later line
 *   RELATIVE to line 0. The two corrections compose additively.
 * - A run whose Compose lines are not all `ceil(L)` tall (an inline
 *   placeholder taller than the box, a `normal` box) is declined (`null`):
 *   the bands below are clipped to each line, and a non-uniform stack could
 *   overlap by more than the one leading pixel the uniform case can.
 * - An integer L (the corpus's inherited 16px × 1.25 = 20px) yields all-zero
 *   deltas and is declined too, so every such run keeps its single draw.
 *
 * Pure + JVM-pinned (`ComposedLinePitchTest`). Composed-WPT only at the
 * call site — the 327-pair dark stage never reaches this.
 */
object ComposedLinePitch {

    /** One line's draw instruction: clip the run to [clipTopPx, clipBottomPx)
     *  in the Text's own coordinates, then paint it translated by [deltaYPx]
     *  (positive = down). The first band opens upward and the last downward
     *  so marks above the first line / below the last are never lost. */
    data class Band(val clipTopPx: Float, val clipBottomPx: Float, val deltaYPx: Float)

    /** The per-line plan for one run; `bands.size == lineCount`. */
    data class Plan(val bands: List<Band>)

    /**
     * How far beyond the run the first/last band's clip reaches, in px. A
     * finite stand-in for "unbounded": Skia accepts it, and no content of
     * this node can lie farther away than that.
     */
    const val OPEN_BAND_PX: Float = 1_000_000f

    /**
     * Build the plan, or `null` when the run needs no re-pitch (then the
     * caller must keep its single, untouched `drawContent()`).
     *
     * @param lineBoxPx the CSS line box L in px (the snap's `refLineBoxPx`).
     * @param lineTopsPx `TextLayoutResult.getLineTop(i)` for every line, in
     *   the Text's own coordinates.
     * @param lineBottomsPx `TextLayoutResult.getLineBottom(i)`, same frame.
     * @param placementYPx where the snap placed the Text inside the snapped
     *   box — `(target − natural) / 2`, the existing symmetric trim.
     * @param prefixExactPx exact line boxes stacked above this run in the
     *   same container (the wave-30 accumulator's phase); 0 for a run that
     *   rounds in isolation, which is what every paragraph does today.
     */
    fun plan(
        lineBoxPx: Float,
        lineTopsPx: FloatArray,
        lineBottomsPx: FloatArray,
        placementYPx: Int,
        prefixExactPx: Float = 0f
    ): Plan? {
        val n = lineTopsPx.size
        // One line has nothing to re-pitch; a malformed pair has nothing to say.
        if (n < 2 || lineBottomsPx.size != n) return null
        // No CSS box (declared `normal`) → the face's own stacking IS the answer.
        if (!lineBoxPx.isFinite() || lineBoxPx <= 0f) return null
        if (!prefixExactPx.isFinite()) return null
        // The platform pitch must be the uniform ceil(L) the span produces —
        // anything else is a stack this plan does not model (see header).
        val pitch = lineTopsPx[1] - lineTopsPx[0]
        if (pitch != ceil(lineBoxPx)) return null
        for (i in 0 until n) {
            if (lineBottomsPx[i] - lineTopsPx[i] != pitch) return null
            if (i > 0 && lineTopsPx[i] - lineTopsPx[i - 1] != pitch) return null
        }
        // round(prefix) once: every line's target is measured from the box top.
        val phase = Math.round(prefixExactPx)
        var anyMove = false
        val bands = ArrayList<Band>(n)
        for (i in 0 until n) {
            // Chromium's accumulated edge for line i, snapped like the snap
            // snaps its box (Math.round is half-up — 62.5 → 63).
            val target = Math.round(prefixExactPx + i * lineBoxPx) - phase
            // Where Compose put the same line inside the snapped box.
            val actual = placementYPx + (lineTopsPx[i] - lineTopsPx[0])
            val delta = target - actual
            if (delta != 0f) anyMove = true
            bands.add(
                Band(
                    clipTopPx = if (i == 0) lineTopsPx[0] - OPEN_BAND_PX else lineTopsPx[i],
                    clipBottomPx = if (i == n - 1) lineBottomsPx[i] + OPEN_BAND_PX
                    else lineBottomsPx[i],
                    deltaYPx = delta
                )
            )
        }
        // An integer L lands every line where Compose already has it.
        return if (anyMove) Plan(bands) else null
    }
}
