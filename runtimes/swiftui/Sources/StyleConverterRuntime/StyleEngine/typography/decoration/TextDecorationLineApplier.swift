//
//  TextDecorationLineApplier.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextDecorationLineApplier {
    static func contribute(_ cfg: TextDecorationLineConfig?, into agg: inout TypographyAggregate) {
        guard let cfg = cfg else { return }
        // Merge OR-wise with any prior contribution (e.g. a shorthand).
        agg.underline     = agg.underline     || cfg.underline
        agg.strikethrough = agg.strikethrough || cfg.lineThrough
        // Lane IOS wave 5 — overline now RENDERS: the flag bridges via
        // TextConfig into PlaceholderLabel, which overlays one Rectangle
        // per rendered line at the line-box top (css-text-decor-3 §2.1;
        // geometry mirrors Compose's overlineSegments, the other half of
        // the compared native pair). Blink (`blink`) remains a no-op —
        // css-text-decor-3 §2.1 explicitly allows UAs to not blink, and
        // Chromium (the reference) does not, so dropping it is spec-
        // conforming, not a silent divergence.
        agg.overline = agg.overline || cfg.overline
        if cfg.underline || cfg.lineThrough || cfg.overline { agg.touched = true }
    }
}
