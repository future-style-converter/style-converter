package com.styleconverter.runtime.typography.text

// Compose runtime — wave 52 lane L8 (vertical-wedges): the INLINE AXIS of a
// vertical writing mode, as two pure decisions.
//
// ## What this file is
// The platform-free half of two css-writing-modes fixes that share one idea:
// under `writing-mode: vertical-*` the element's INLINE axis is the screen's
// vertical axis, and two places in the runtimes forgot that.
//
//   1. [chAdvanceIsVertical] — css-values-4 §6.1.1: `1ch` is "the advance
//      measure of the '0' glyph … in the inline axis of the element". Under
//      `text-orientation: upright` the '0' stands up, so its inline advance is
//      its VERTICAL advance (ascent + descent, the em-box height); under
//      `sideways` / `mixed` (where '0' rotates) and in every horizontal mode
//      it stays the horizontal x-advance. Both twins' ChUnitMetrics used the
//      x-advance unconditionally — measured on wave51-fix css-writing-modes/
//      ch-units-vrl-005..008: the orange `width: 5ch` upright box drew 60 px
//      (iOS) / 55 px (Android) where the ref draws 120 (5 × 24 at 20 px Inter).
//   2. [orthogonalBudget] / [uprightBudget] — css-writing-modes-4 §7.3.1
//      (Available Space in Orthogonal Flows): an orthogonal flow whose
//      available inline size is indefinite does NOT get an infinite line; it
//      wraps against the nearest ancestor's DEFINITE block size if any, else
//      the initial containing block's block extent, and then sizes
//      fit-content. Both upright typesetters declined on that indefinite case
//      (Compose `VerticalRunIntrinsics` budget = null when the height
//      constraint is unbounded; iOS `VerticalTextFlowLayout` nil proposal) and
//      fell back to the rotated run (Android, 55×72) or the horizontal label
//      (iOS, 60×25) — same eight cells.
//
// No Compose types on purpose: the SwiftUI twin
// (runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/
//  writing/VerticalInlineAxis.swift) is this file in Swift, function for
// function, so the two natives cannot disagree about WHICH ch boxes measure
// the vertical advance or WHAT budget an orthogonal run wraps against.
//
// ## Blast radius (tools/titan/results/wave52-vertical-wedges/census.json)
// [chAdvanceIsVertical] answers true for exactly 18 ch-carrying components
// in 8 documents (css-writing-modes/ch-units-vrl-001..008), all failing on
// both natives today; the other 151 ch carriers in the corpus are horizontal
// (136) or vertical-but-not-upright (15) and keep the x-advance byte for
// byte. The §7.3.1 fallback reaches 10 upright-leaf documents + 5 mixed-CJK
// documents, every one failing on both natives today.

object VerticalInlineAxis {

    /**
     * Does `ch` measure the VERTICAL advance of '0' for an element in this
     * writing mode + text orientation?
     *
     * css-values-4 §6.1.1 + css-writing-modes-4 §5.1: only the two
     * `vertical-*` modes have an upright option at all — `sideways-*` fixes
     * every glyph sideways (its `text-orientation` does not apply), and a
     * horizontal mode's inline axis is horizontal by definition. Under
     * `mixed` the '0' (Vertical_Orientation R) rotates, so it keeps the
     * x-advance — the same per-character rule `VerticalTextFlow.runOrientation`
     * applies to the run. Callers pass the MERGED values: `writing-mode`
     * inherits through both natives' channels, `text-orientation` does not
     * (neither inherited set carries it), so a `td` under an upright `tr`
     * (ch-units-vrl-001/002) still measures the x-advance — a known gap,
     * identical on both twins (lane note).
     */
    fun chAdvanceIsVertical(
        writingMode: WritingModeValue,
        textOrientation: TextOrientationValue,
    ): Boolean {
        // Only vertical-rl / vertical-lr can stand glyphs upright.
        val vertical = writingMode == WritingModeValue.VERTICAL_RL ||
            writingMode == WritingModeValue.VERTICAL_LR
        // …and only the explicit `upright` keyword stands '0' up.
        return vertical && textOrientation == TextOrientationValue.UPRIGHT
    }

    /**
     * css-writing-modes-4 §7.3.1 — the available inline size of an
     * orthogonal flow whose containing block's block size is indefinite.
     *
     * @param nearestDefiniteBlockSizePx the nearest ancestor's DEFINITE block
     *   extent along the flow's inline axis (its used `height` under a
     *   horizontal parent — what both runtimes' containing-block channels
     *   publish, and the closest available reading of the spec's
     *   "max-block-size of the nearest ancestor" clause; a definite
     *   `max-height` alone is NOT published by either channel today, see
     *   the lane note), or null when none is definite.
     * @param icbBlockExtentPx the initial containing block's extent on that
     *   axis — the composed capture's 568 px viewport height (Compose
     *   `LocalComposedViewport.heightPx`, iOS `StyleViewport.height`), or
     *   null outside a capture that publishes one.
     * @return the wrap budget in px, or null when neither input is a
     *   positive finite number — the caller then DECLINES exactly as before
     *   (the dark stage publishes no viewport, so it is byte-identical).
     */
    fun orthogonalBudget(
        nearestDefiniteBlockSizePx: Double?,
        icbBlockExtentPx: Double?,
    ): Double? {
        // A definite ancestor block size wins: it is the containing block's
        // own extent, which the ICB clause only stands in for.
        nearestDefiniteBlockSizePx?.takeIf { it.isFinite() && it > 0.0 }?.let { return it }
        // Else the ICB — Chromium's own fallback for `height: auto` chains.
        return icbBlockExtentPx?.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * The budget an upright measure plans against: a BOUNDED constraint (a
     * definite available inline size) always wins; only an indefinite one
     * takes the §7.3.1 [orthogonalBudget] fallback; null still declines.
     *
     * Spelled as its own function so the measure policies on both natives
     * make the pick through one pinned rule instead of two inline elvises.
     */
    fun uprightBudget(boundedPx: Double?, fallbackPx: Double?): Double? =
        // Definite beats fallback; a null fallback leaves the historical decline.
        boundedPx ?: fallbackPx
}
