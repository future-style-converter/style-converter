//
//  TextDecorationStyleApplier.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextDecorationStyleApplier {
    static func contribute(_ cfg: TextDecorationStyleConfig?, into agg: inout TypographyAggregate) {
        guard let p = cfg?.pattern else { return }
        agg.decorationStyle = p
        agg.touched = true
    }
}
