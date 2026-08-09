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
//  the case once the raster is decoded — css-images-3 §5.2 derives the ratio
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
//  ## What it deliberately does NOT model
//
//  min-/max-width/height clamping (§10.4) and `box-sizing: border-box`'s
//  padding subtraction are NOT applied here — those already live in the
//  platforms' own modifier chains, which run BEFORE the content is measured,
//  so duplicating them would apply each constraint twice. This answers only
//  "given the box the chain produced, how big is the content".
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
}
