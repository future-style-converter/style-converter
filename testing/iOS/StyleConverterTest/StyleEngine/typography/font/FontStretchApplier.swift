//
//  FontStretchApplier.swift
//  StyleEngine/typography/font — Phase 6.
//
//  SwiftUI has no native width-transform API for Text. We record the
//  percent value so a future shader-based renderer can consume it; the
//  live applier is an identity contribution with a TODO.
//

import Foundation

enum FontStretchApplier {
    // TODO(phase-6+): apply width transform when a custom text renderer
    // lands. For now, preserve the value in the aggregate for audit.
    static func contribute(_ cfg: FontStretchConfig?, into agg: inout TypographyAggregate) {
        guard let p = cfg?.percent else { return }
        agg.fontStretchPercent = p
        // `touched` stays false-unchanged here: a font-stretch alone doesn't
        // need to trigger TypographyApplier modifiers. Other fields flip it.
    }
}
