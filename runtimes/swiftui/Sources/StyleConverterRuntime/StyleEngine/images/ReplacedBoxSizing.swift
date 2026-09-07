//
//  ReplacedBoxSizing.swift
//  StyleEngine/images — wave-39 lane A2
//
//  CSS 2.1 §10.3.2 / §10.6.2 used-size rules for a REPLACED element whose
//  content now has an intrinsic size.
//
//  PURE and platform-free on purpose: it is the one piece of this lane both
//  natives must agree on to the pixel, so it is unit-tested off-device on both
//  and is a line-for-line mirror of Kotlin images/ReplacedBoxSizing.kt. Nothing
//  here touches SwiftUI, a UIImage, or a modifier — the caller turns a `Mode`
//  into its platform's sizing call.
//
//  ## The four cases, and where they come from
//
//  CSS 2.1 gives `width: auto` / `height: auto` on a replaced element a cascade
//  of rules; with an intrinsic width, height AND ratio all available (which is
//  the case once the raster is decoded — css-images-3 §4.1 derives the ratio
//  from the raster's own dimensions) they collapse to four:
//
//  | declared width | declared height | used content box                      |
//  |----------------|-----------------|---------------------------------------|
//  | definite       | definite        | exactly the declared box (§10.3.2 #1)  |
//  | definite       | auto            | height = width ÷ ratio    (§10.6.2 #2) |
//  | auto           | definite        | width  = height × ratio   (§10.3.2 #2) |
//  | auto           | auto            | the intrinsic size        (§10.3.2 #4) |
//
//  The two ratio rows are where the empty-box captures came from: with no
//  content there was no ratio, so a box with one declared axis had nothing to
//  derive the other from and collapsed.
//
//  ## §10.4 constraint-violation sizing (wave-42 lane W6)
//
//  `constrainAutoSize` adds CSS 2.1 §10.4's min-/max- resolution for the
//  BOTH-AXES-AUTO row: "for replaced elements with an intrinsic ratio and both
//  'width' and 'height' specified as 'auto'", a min/max violation re-solves
//  the OTHER axis through the ratio via the spec's ten-row table. Without it
//  the css-ui box-sizing 010..025 family painted the delivered raster at
//  intrinsic size — box-sizing-016's 70px square rendered ~150px wide and
//  wrapped (the measured wave-41 T2 blocker in tools/titan/svg-preraster.mjs).
//  `box-sizing: border-box`'s band subtraction still lives with the CALLER
//  (ReplacedImageContent), which alone can see the padding/border configs;
//  this enum stays pure arithmetic on CONTENT-BOX px so both natives pin the
//  same numbers off-device. The single-declared-axis modes still delegate
//  min/max to the platform chain (which clamps the box), because a declared
//  axis is never re-solved by §10.4's replaced-element table.
//

import Foundation

enum ReplacedBoxSizing {

    /// How the caller must size the replaced CONTENT inside the box its
    /// modifier chain already produced.
    enum Mode: Equatable {
        /// Both axes definite — the content fills the box the chain sized.
        /// `object-fit` decides how the raster maps into it.
        case fillBoth
        /// Width definite, height auto — fill the width, derive the height
        /// from the intrinsic ratio (§10.6.2 rule 2).
        case widthFillsRatioHeight
        /// Height definite, width auto — fill the height, derive the width
        /// from the intrinsic ratio (§10.3.2 rule 2).
        case heightFillsRatioWidth
        /// Neither axis definite — the content is its intrinsic size and the
        /// box hugs it (§10.3.2 rule 4). The case the css-writing-modes
        /// intrinsic-size-contribution family asserts on.
        case intrinsic
    }

    /// Pick the mode for one replaced box.
    ///
    /// - Parameters:
    ///   - widthDefinite: the box's inline axis was given a used size by the
    ///     style chain (a resolved length or a percentage).
    ///   - heightDefinite: same, block axis.
    ///   - aspectRatio: the content's intrinsic width ÷ height, or nil when the
    ///     raster is degenerate.
    ///
    /// A nil ratio DOWNGRADES the two single-axis rows to `.intrinsic` rather
    /// than guessing: §10.3.2's rule 2 is written "if … has an intrinsic
    /// ratio", and its absence sends the used size to the intrinsic dimension.
    /// Falling through to `.fillBoth` instead would stretch the raster across
    /// an axis nothing declared — the exact silent distortion this table
    /// exists to prevent.
    static func mode(widthDefinite: Bool, heightDefinite: Bool, aspectRatio: Double?) -> Mode {
        let ratioUsable = (aspectRatio.map { $0.isFinite && $0 > 0 }) ?? false
        if widthDefinite && heightDefinite { return .fillBoth }
        if widthDefinite && ratioUsable { return .widthFillsRatioHeight }
        if heightDefinite && ratioUsable { return .heightFillsRatioWidth }
        return .intrinsic
    }

    /// Is this axis DEFINITE for the purposes of the table above?
    ///
    /// Mirrors the Kotlin twin's `ComponentRenderer.hasDefiniteSize` decision
    /// row for row, expressed against the iOS `LengthValue` enum instead of the
    /// raw wire JSON:
    ///   * `.exact(px:)` — an absolute length the parser resolved (`em`/`vw`/
    ///     `calc` serialise with no px and land in `.relative`/`.calc`, which
    ///     are correctly NOT definite here);
    ///   * `.relative` in PERCENT — percentages resolve against the parent at
    ///     layout time, so the box is definite even though the number is not
    ///     known yet;
    ///   * everything else (auto, intrinsic keywords, fr, calc, none, unknown,
    ///     and a nil/absent declaration) is indefinite.
    static func isDefinite(_ value: LengthValue?) -> Bool {
        switch value {
        case .some(.exact):
            return true
        case .some(.relative(_, let unit, _)):
            return unit == .percent
        default:
            return false
        }
    }

    /// One §10.4-resolved used CONTENT size, px. A plain pair rather than a
    /// CGSize so the Kotlin twin mirrors it field-for-field (and Foundation
    /// stays the only import).
    struct UsedSize: Equatable {
        let widthPx: Double   // used content-box width, CSS px
        let heightPx: Double  // used content-box height, CSS px
    }

    /// CSS 2.1 §10.4 used width/height for a replaced element whose width AND
    /// height are both `auto` — the `.intrinsic` row, and ONLY that row.
    ///
    /// Inputs are all CONTENT-BOX px: the caller has already subtracted the
    /// `box-sizing: border-box` padding+border band from each declared bound
    /// (or passed the declared value verbatim under content-box). A nil bound
    /// means "not declared" (or declared `none`/unresolvable), i.e. 0 for the
    /// mins and +∞ for the maxes — §10.4's initial values.
    ///
    /// The tentative used size is the intrinsic size itself (§10.3.2 rule 4 /
    /// §10.6.2 rule 3 — a decoded raster always has both dimensions). With a
    /// usable ratio a violated bound re-solves the OTHER axis via the spec's
    /// ten-row table (max-height: 70px on a 1:1 raster yields 70×70 — the
    /// css-ui box-sizing-010..025 geometry); without one each axis clamps
    /// independently. With no bounds declared this is the identity on the
    /// intrinsic size, so every pre-wave-42 caller renders byte-identical.
    ///
    /// Line-for-line mirror of Kotlin `ReplacedBoxSizing.constrainAutoSize`.
    static func constrainAutoSize(
        intrinsicWidthPx: Double,
        intrinsicHeightPx: Double,
        aspectRatio: Double?,
        minWidthPx: Double? = nil,
        maxWidthPx: Double? = nil,
        minHeightPx: Double? = nil,
        maxHeightPx: Double? = nil
    ) -> UsedSize {
        // Tentative used size = the intrinsic size (§10.3.2 rule 4).
        let w = intrinsicWidthPx
        let h = intrinsicHeightPx
        // §10.4 preamble: undeclared min is 0; a defensive floor keeps the
        // arithmetic total against a malformed negative bound.
        let minW = max(minWidthPx ?? 0, 0)
        let minH = max(minHeightPx ?? 0, 0)
        // §10.4 (replaced-element algorithm): "use max(min, max)" — a max
        // below its min is raised to the min, never the other way round.
        let maxW = max(maxWidthPx ?? .infinity, minW)
        let maxH = max(maxHeightPx ?? .infinity, minH)
        // Same usability gate as `mode`: the table's ratio terms divide by
        // the tentative axes, so a degenerate raster takes the no-ratio path.
        let ratioUsable = (aspectRatio.map { $0.isFinite && $0 > 0 }) ?? false
        guard ratioUsable, w > 0, h > 0 else {
            // No ratio ⇒ nothing links the axes: clamp each independently
            // (§10.4's non-ratio re-solve degenerates to the clamp).
            return UsedSize(widthPx: min(max(w, minW), maxW),
                            heightPx: min(max(h, minH), maxH))
        }
        // Which bounds does the tentative size violate? These select the
        // §10.4 table row; the two-violation rows must be tested FIRST
        // because the single-violation rows also match their inputs.
        let overW = w > maxW
        let underW = w < minW
        let overH = h > maxH
        let underH = h < minH
        switch (overW, underW, overH, underH) {
        // Row 6/7 — both too large: shrink by the MOST violated axis (the
        // smaller scale factor), floor the derived axis at its min.
        case (true, _, true, _):
            return maxW / w <= maxH / h
                ? UsedSize(widthPx: maxW, heightPx: max(minH, maxW * h / w))
                : UsedSize(widthPx: max(minW, maxH * w / h), heightPx: maxH)
        // Row 8/9 — both too small: grow by the MOST violated axis (the
        // larger scale factor), CAP the derived axis at its max. Both arms
        // read `min(max-…, …)`: §10.4 row 8 is "min(max-width,
        // min-height·w/h), min-height" and row 9 its mirror "min-width,
        // min(max-height, min-width·h/w)". The derived axis is already ≥ its
        // own min by construction (the driving axis is the MORE violated one,
        // so its scale factor over-satisfies the other min), which is why the
        // spec caps rather than floors here — flooring at the min instead
        // would let the derived axis blow straight past a declared max (50×50
        // raster, min-width 100 / min-height 70 / max-height 70 painted
        // 100×100, not the ref's 100×70).
        case (_, true, _, true):
            return minW / w <= minH / h
                ? UsedSize(widthPx: min(maxW, minH * w / h), heightPx: minH)
                : UsedSize(widthPx: minW, heightPx: min(maxH, minW * h / w))
        // Row 10 — squeezed in opposite directions: both bounds win and the
        // ratio is deliberately abandoned (the spec's own choice).
        case (_, true, true, _):
            return UsedSize(widthPx: minW, heightPx: maxH)
        case (true, _, _, true):
            return UsedSize(widthPx: maxW, heightPx: minH)
        // Rows 2..5 — one violated axis takes its bound; the other re-solves
        // through the ratio, then clamps to ITS opposite bound.
        case (true, _, _, _):
            return UsedSize(widthPx: maxW, heightPx: max(maxW * h / w, minH))
        case (_, true, _, _):
            return UsedSize(widthPx: minW, heightPx: min(minW * h / w, maxH))
        case (_, _, true, _):
            return UsedSize(widthPx: max(maxH * w / h, minW), heightPx: maxH)
        case (_, _, _, true):
            return UsedSize(widthPx: min(minH * w / h, maxW), heightPx: minH)
        // Row 1 — no violation: the tentative size IS the used size.
        default:
            return UsedSize(widthPx: w, heightPx: h)
        }
    }
}
