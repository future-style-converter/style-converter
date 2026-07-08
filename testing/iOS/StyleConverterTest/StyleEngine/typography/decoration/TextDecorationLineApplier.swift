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
        // overline & blink have no SwiftUI path — captured as aggregate
        // fields would force a new struct field; skipped intentionally.
        if cfg.underline || cfg.lineThrough { agg.touched = true }
    }
}
