//
//  FontKerningApplier.swift
//  StyleEngine/typography/font — Phase 6.
//
//  `none` forces letterSpacing to 0 so ligatures don't tighten. `auto` /
//  `normal` leave the platform default. This means the applier only
//  touches the aggregate when the mode is `none`.
//

import Foundation

enum FontKerningApplier {
    static func contribute(_ cfg: FontKerningConfig?, into agg: inout TypographyAggregate) {
        guard cfg?.mode == FontKerningMode.none else { return }
        // Force-zero letter spacing suppresses the default UIFont kerning.
        agg.letterSpacingPx = 0
        agg.touched = true
    }
}
