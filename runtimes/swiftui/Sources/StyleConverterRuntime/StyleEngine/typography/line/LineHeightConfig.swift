//
//  LineHeightConfig.swift
//  StyleEngine/typography/line — Phase 6.
//
//  `line-height` accepts `normal`, a unitless multiplier, a length, or a
//  percentage. The CSS parser pre-resolves numerics to `{ px: N }` where
//  possible; unitless / %-forms that depend on the rendered font size
//  arrive as raw numbers that we treat as "absolute" at extract time.
//

import CoreGraphics

/// Explicit line-box height in points. Nil → inherit. SwiftUI's
/// `.lineSpacing(...)` expects the *extra* space, so TypographyApplier
/// subtracts the font size at emit time.
struct LineHeightConfig: Equatable {
    var px: CGFloat? = nil
    /// Unitless multiplier (e.g. `line-height: 2`) carried through when
    /// the IR couldn't pre-resolve to absolute pixels. The applier
    /// multiplies this by the rendered font-size at emit time.
    var multiplier: CGFloat? = nil
    /// Wave 22 (lane FONT) — the author DECLARED the `normal` keyword
    /// (css-fonts-4 §4.3: what `font: 92px Arial` resets line-height to).
    /// Travels ALONGSIDE `multiplier` (the wire pairs the keyword with a legacy
    /// 1.2 compatibility value), not instead of it: under WPT capture the flag
    /// wins and the run uses the face's own metrics; elsewhere the number wins
    /// so the committed dark-stage baselines stay byte-identical.
    /// See LineHeightNormal.lineBoxSource for the full table + stated risk.
    var isNormalKeyword: Bool = false
}
