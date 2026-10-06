//
//  VerticalInlineAxis.swift
//  StyleEngine/typography/writing — wave 52 lane L8 (vertical-wedges): the
//  INLINE AXIS of a vertical writing mode, as two pure decisions.
//
//  The SwiftUI twin of
//  runtimes/compose/src/main/java/com/styleconverter/runtime/typography/text/
//  VerticalInlineAxis.kt — the same file in Swift, function for function, so
//  the two natives cannot disagree about WHICH ch boxes measure the vertical
//  advance or WHAT budget an orthogonal run wraps against.
//
//  ## What it decides
//    1. `chAdvanceIsVertical` — css-values-4 §6.1.1: `1ch` is the advance of
//       the '0' glyph "in the inline axis of the element". Under
//       `vertical-*` + `text-orientation: upright` the '0' stands up, so its
//       inline advance is its VERTICAL advance (ascent + descent); under
//       `sideways` / `mixed` (where '0' rotates) and in every horizontal mode
//       it stays the horizontal x-advance. ChUnitMetrics used the x-advance
//       unconditionally — measured on wave51-fix css-writing-modes/
//       ch-units-vrl-005..008: the orange `width: 5ch` upright box drew 60 px
//       on iOS where the ref draws 120 (5 × 24 at 20 px Inter).
//    2. `orthogonalBudget` / `uprightBudget` — css-writing-modes-4 §7.3.1
//       (Available Space in Orthogonal Flows): an orthogonal flow whose
//       available inline size is indefinite wraps against the nearest
//       ancestor's DEFINITE block size if any, else the initial containing
//       block's extent, then sizes fit-content. `VerticalUprightTextFlowLayout`
//       declined on the nil proposal instead and placed the horizontal
//       label (60×25) — same cells.
//
//  ## Blast radius (tools/titan/results/wave52-vertical-wedges/census.json)
//  `chAdvanceIsVertical` is true for exactly 18 ch-carrying components in 8
//  documents (css-writing-modes/ch-units-vrl-001..008), all failing on both
//  natives today; the other 151 ch carriers keep the x-advance byte for
//  byte. The §7.3.1 fallback reaches 10 upright-leaf + 5 mixed-CJK
//  documents, every one failing on both natives today.
//

import Foundation

enum VerticalInlineAxis {

    /// Does `ch` measure the VERTICAL advance of '0' for an element in this
    /// writing mode + text orientation?
    ///
    /// css-values-4 §6.1.1 + css-writing-modes-4 §5.1: only the two
    /// `vertical-*` modes have an upright option at all — `sideways-*` fixes
    /// every glyph sideways (its `text-orientation` does not apply), and a
    /// horizontal mode's inline axis is horizontal by definition. Under
    /// `mixed` the '0' (Vertical_Orientation R) rotates, so it keeps the
    /// x-advance — the same per-character rule `VerticalTextFlow.runOrientation`
    /// applies to the run. Callers pass the MERGED values: `writing-mode`
    /// inherits through both natives' channels, `text-orientation` does not
    /// (neither inherited set carries it), so a `td` under an upright `tr`
    /// (ch-units-vrl-001/002) still measures the x-advance — a known gap,
    /// identical on both twins (lane note).
    static func chAdvanceIsVertical(writingMode: WritingModeValue,
                                    textOrientation: TextOrientationValue) -> Bool {
        // Only vertical-rl / vertical-lr can stand glyphs upright.
        let vertical = writingMode == .verticalRl || writingMode == .verticalLr
        // …and only the explicit `upright` keyword stands '0' up.
        return vertical && textOrientation == .upright
    }

    /// css-writing-modes-4 §7.3.1 — the available inline size of an
    /// orthogonal flow whose containing block's block size is indefinite.
    ///
    /// - Parameters:
    ///   - nearestDefiniteBlockSizePx: the nearest ancestor's DEFINITE block
    ///     extent along the flow's inline axis (its used `height` under a
    ///     horizontal parent — what the containing-block channel publishes,
    ///     and the closest available reading of the spec's "max-block-size
    ///     of the nearest ancestor" clause; a definite `max-height` alone is
    ///     NOT published today, see the lane note), or nil when none is.
    ///   - icbBlockExtentPx: the initial containing block's extent on that
    ///     axis — the composed capture's 568 px viewport height
    ///     (`StyleViewport.height`), or nil outside such a capture.
    /// - Returns: the wrap budget in px, or nil when neither input is a
    ///   positive finite number — the caller then DECLINES exactly as before.
    static func orthogonalBudget(nearestDefiniteBlockSizePx: Double?,
                                 icbBlockExtentPx: Double?) -> Double? {
        // A definite ancestor block size wins: it is the containing block's
        // own extent, which the ICB clause only stands in for.
        if let d = nearestDefiniteBlockSizePx, d.isFinite, d > 0 { return d }
        // Else the ICB — Chromium's own fallback for `height: auto` chains.
        if let icb = icbBlockExtentPx, icb.isFinite, icb > 0 { return icb }
        return nil
    }

    /// The budget an upright measure plans against: a BOUNDED proposal (a
    /// definite available inline size) always wins; only an indefinite one
    /// takes the §7.3.1 `orthogonalBudget` fallback; nil still declines.
    ///
    /// Spelled as its own function so the layouts on both natives make the
    /// pick through one pinned rule instead of two inline coalesces.
    static func uprightBudget(boundedPx: Double?, fallbackPx: Double?) -> Double? {
        // Definite beats fallback; a nil fallback leaves the historical decline.
        boundedPx ?? fallbackPx
    }
}
