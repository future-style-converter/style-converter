//
//  TextShadowApplier.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextShadowApplier {
    static func contribute(_ cfg: TextShadowConfig?, into agg: inout TypographyAggregate) {
        guard let layers = cfg?.layers, let first = layers.first else { return }
        // First layer kept for legacy single-shadow consumers.
        agg.textShadow = first
        // Fidelity wave 2 — keep EVERY layer. css-text-decor-3 §4 allows
        // a comma list; PlaceholderLabel chains one `.shadow(...)` per
        // layer directly on the glyph run (Typography_C18 declares two).
        // The old box-level TextShadowMod haloed the whole painted
        // container instead of the glyphs — removed.
        agg.textShadowLayers = layers
        agg.touched = true
    }
}
