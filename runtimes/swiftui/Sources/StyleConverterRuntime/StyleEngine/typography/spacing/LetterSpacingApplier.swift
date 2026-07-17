//
//  LetterSpacingApplier.swift
//  StyleEngine/typography/spacing — Phase 6.
//

import Foundation

enum LetterSpacingApplier {
    static func contribute(_ cfg: LetterSpacingConfig?, into agg: inout TypographyAggregate) {
        if let px = cfg?.px {
            // Later writes win — matches the cascade. FontKerning.none may
            // already have forced 0; a later `letter-spacing` overrides it.
            agg.letterSpacingPx = px
            agg.touched = true
        }
        // Lane IOS-TEXT fix 3 — em/rem tracking is parked on the
        // aggregate and resolved to px in TypographyExtractor once the
        // element font size is known (css-values-4 §6.1: em on a
        // non-font-size property refers to the element's own size).
        if let rel = cfg?.relative {
            agg.letterSpacingRelative = rel
            agg.touched = true
        }
    }
}
