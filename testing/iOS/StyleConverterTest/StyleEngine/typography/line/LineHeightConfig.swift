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
}
