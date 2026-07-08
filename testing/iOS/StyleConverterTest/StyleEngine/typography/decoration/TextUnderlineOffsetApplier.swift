//
//  TextUnderlineOffsetApplier.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextUnderlineOffsetApplier {
    static func contribute(_ cfg: TextUnderlineOffsetConfig?, into agg: inout TypographyAggregate) {
        guard let px = cfg?.px else { return }
        agg.underlineOffsetPx = px   // captured, no SwiftUI path
    }
}
