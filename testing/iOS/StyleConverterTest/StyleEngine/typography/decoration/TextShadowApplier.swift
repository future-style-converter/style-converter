//
//  TextShadowApplier.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextShadowApplier {
    static func contribute(_ cfg: TextShadowConfig?, into agg: inout TypographyAggregate) {
        guard let first = cfg?.layers.first else { return }
        // Keep only the first layer — SwiftUI Text's `.shadow` doesn't
        // stack like CSS does. TODO(phase-6+): multi-layer via CALayer.
        agg.textShadow = first
        agg.touched = true
    }
}
