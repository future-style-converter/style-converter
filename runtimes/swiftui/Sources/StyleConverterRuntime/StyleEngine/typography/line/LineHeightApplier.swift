//
//  LineHeightApplier.swift
//  StyleEngine/typography/line — Phase 6.
//

import Foundation

enum LineHeightApplier {
    static func contribute(_ cfg: LineHeightConfig?, into agg: inout TypographyAggregate) {
        guard let cfg = cfg else { return }
        if let px = cfg.px {
            // Absolute line-box height; TypographyApplier subtracts the
            // font size before calling `.lineSpacing(_:)`.
            agg.lineHeightPx = px
            agg.touched = true
        } else if let mult = cfg.multiplier {
            // Unitless multiplier (CSS `line-height: 2`). Resolve to
            // px × fontSize at the end of the typography pass — TypographyExtractor
            // finalises the aggregate after every contributor has run, at which
            // point fontSizePx is guaranteed to be set if the IR carried one.
            agg.lineHeightMultiplier = mult
            agg.touched = true
        }
    }
}
