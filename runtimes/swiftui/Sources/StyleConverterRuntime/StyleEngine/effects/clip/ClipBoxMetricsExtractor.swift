//
//  ClipBoxMetricsExtractor.swift
//  StyleEngine/effects/clip — wave 46 (lane Y4).
//
//  The css-masking-1 §5.1 half of clip-path extraction: the
//  `<geometry-box>` keyword and the element's own box metrics
//  (ClipBoxMetrics) the applier turns into a reference box at draw time.
//  Split from ClipExtractor (the shape-wire reader) to keep both files
//  near the house size target. Twin of Android's
//  ClipBoxGeometryExtractor.kt.
//

import SwiftUI

enum ClipBoxMetricsExtractor {

    /// css-masking-1 §5.1 keyword → `ClipGeometryBox`. The SVG-only
    /// keywords take the spec's used value for an element with a CSS
    /// layout box (fill-box → content-box, stroke-box / view-box →
    /// border-box); an unknown keyword keeps the border-box initial
    /// rather than dropping the clip.
    static func parseGeometryBox(_ keyword: String) -> ClipGeometryBox {
        switch keyword.lowercased() {
        case "margin-box": return .marginBox
        case "padding-box": return .paddingBox
        case "content-box", "fill-box": return .contentBox
        default: return .borderBox
        }
    }

    /// The element's own box metrics, read through the SAME extractors
    /// the layout chain uses (Margin / Padding / BorderSide /
    /// BorderRadius), so the reference box the applier derives from its
    /// border-box rect is built from the numbers that laid the box out.
    /// Only lengths already in px resolve here (the wire's `{px}` form,
    /// or a relative unit's pxFallback); `auto` / unresolved read as 0 —
    /// stated limit, no corpus clip-path carries one. Read only once a
    /// clip exists: this extractor runs for every component.
    static func extract(from properties: [IRProperty]) -> ClipBoxMetrics {
        var m = ClipBoxMetrics()
        func px(_ v: LengthValue) -> CGFloat {
            switch v {
            case .exact(let px): return CGFloat(px)
            case .relative(_, _, let fallback): return CGFloat(fallback ?? 0)
            default: return 0
            }
        }
        if let margin = MarginExtractor.extract(from: properties) {
            m.marginTop = px(margin.top); m.marginRight = px(margin.right)
            m.marginBottom = px(margin.bottom); m.marginLeft = px(margin.left)
        }
        if let padding = PaddingExtractor.extract(from: properties) {
            m.paddingTop = max(0, px(padding.top)); m.paddingRight = max(0, px(padding.right))
            m.paddingBottom = max(0, px(padding.bottom)); m.paddingLeft = max(0, px(padding.left))
        }
        if let borders = BorderSideExtractor.extract(from: properties) {
            // USED width: 0 unless the side actually paints (CSS2 §8.5.3
            // none/hidden → 0) — BorderSideConfig.hasBorder encodes that.
            func used(_ side: BorderSideConfig) -> CGFloat {
                side.hasBorder ? (side.effectiveWidth ?? 0) : 0
            }
            m.borderTop = used(borders.top); m.borderRight = used(borders.end)
            m.borderBottom = used(borders.bottom); m.borderLeft = used(borders.start)
        }
        if let radius = BorderRadiusExtractor.extract(from: properties) {
            m.radius = radius
        }
        return m
    }

}
