//
//  TextTransformApplier.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextTransformApplier {
    static func contribute(_ cfg: TextTransformConfig?, into agg: inout TypographyAggregate) {
        guard let tc = cfg?.textCase else { return }
        agg.textCase = tc
        agg.touched = true
    }
}
