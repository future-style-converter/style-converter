//
//  RatioInlineSize.swift
//  StyleEngine/sizing — wave 38 (lane N7).
//
//  ONE pure predicate: does a box's preferred aspect ratio already
//  determine its INLINE size, so the composed-WPT block-flow fill must
//  keep its hands off the width slot?
//
//  css-sizing-4 §4.1 ("Sizing by Aspect Ratio"): a block-level box with a
//  preferred aspect ratio and a DEFINITE block size does NOT take the
//  CSS 2.1 §10.3.3 stretch-fit inline size — its automatic inline size is
//  the block size multiplied by the ratio. Chromium implements exactly
//  that: `<div style="height:100px; aspect-ratio:1/1">` paints 100×100,
//  not viewport-wide × 100 (tools/wpt/css/css-sizing/aspect-ratio/
//  block-aspect-ratio-002.html, whose ref IS ref-filled-green-100px-
//  square.xht).
//
//  iOS regressed that rule not in SizeApplierMath — whose ratio fill is
//  correct — but one stage EARLIER: ComponentRenderer's
//  `wptBlockFlowFillWidth` fold writes the containing block's content
//  width into the still-nil width slot for every in-flow block box, so by
//  the time SizeApplierMath ran, `effW` was already 358 and the
//  "fill in the MISSING axis" branch had nothing to fill. Measured on the
//  frozen wave37-final captures: seven css-sizing block-aspect-ratio
//  cells and two css-values calc-size-aspect-ratio cells rendered a
//  full-canvas green BAR where web, Android and the Chromium ref all
//  paint the ratio-derived square (iOS 0.9027 / 0.9045 vs web 0.999).
//
//  Compose needs no twin of this file: its SizingApplier hands the ratio
//  to Compose's own `Modifier.aspectRatio`, which resolves the auto axis
//  during measurement — Android already passes every cell this predicate
//  unblocks, so there is no parallel fold to gate.
//

// Foundation only — the predicate is pure over SizeConfig so
// RatioInlineSizeTests can pin it without a SwiftUI view tree.
import Foundation

enum RatioInlineSize {

    /// True when this box's ratio + a definite BLOCK size fully determine
    /// the inline size, i.e. when SizeApplierMath's "fill the missing
    /// axis" branch WILL synthesise a width. Callers use it to suppress
    /// an inline-axis stretch that would pre-empt the ratio.
    ///
    /// Three conditions, all necessary:
    ///  1. a usable ratio — `ratio > 0`. `aspect-ratio: auto` alone
    ///     carries ratio 0 (AspectRatioValue) and determines nothing; the
    ///     `auto W/H` form keeps its W/H fallback, which for a non-
    ///     replaced box IS the used ratio, so `isAuto` is deliberately
    ///     NOT part of the test (same gate SizeApplierMath uses).
    ///  2. an auto INLINE size — a declared width always wins
    ///     (css-sizing-4 §4: the ratio only fills an automatic axis), and
    ///     a declared width also means the fold never fired anyway.
    ///  3. a DEFINITE block size. Only `.exact(px:)` counts: that is the
    ///     one LengthValue shape whose px value survives to
    ///     SizeApplierMath without a resolution context, so it is exactly
    ///     the set where the ratio fill is guaranteed to produce a width.
    ///     Percent / calc / intrinsic block sizes are conservatively NOT
    ///     definite here — suppressing the fill-width for a box whose
    ///     ratio then failed to synthesise a width would collapse it to
    ///     its ideal size, a strictly worse render than today's stretch.
    ///     (`height: 100%` on css-sizing abspos-009 is the live example;
    ///     it stays on the old path — see the lane notes.)
    static func determinesInlineSize(_ c: SizeConfig) -> Bool {
        // (1) usable ratio.
        guard let ar = c.aspectRatio, ar.ratio > 0 else { return false }
        // (2) the author pinned the inline axis → nothing to derive.
        guard c.width == nil else { return false }
        // (3) definite block size, in the declared coordinate system
        // (content box under the WPT content-box default, border box
        // otherwise) — the ratio applies in that same system, which is
        // why SizeApplierMath derives the width BEFORE inflating.
        if case .exact = c.height { return true }
        return false
    }
}
