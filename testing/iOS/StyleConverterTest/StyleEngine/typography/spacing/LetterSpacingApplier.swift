//
//  LetterSpacingApplier.swift
//  StyleEngine/typography/spacing — Phase 6.
//

import Foundation

enum LetterSpacingApplier {
    static func contribute(_ cfg: LetterSpacingConfig?, into agg: inout TypographyAggregate) {
        guard let px = cfg?.px else { return }
        // Later writes win — matches the cascade. FontKerning.none may
        // already have forced 0; a later `letter-spacing` overrides it.
        agg.letterSpacingPx = px
        agg.touched = true
    }
}
