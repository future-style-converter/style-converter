//
//  FontStyleApplier.swift
//  StyleEngine/typography/font — Phase 6.
//
//  Italic folds into the aggregate. Two render vehicles consume it:
//  the text label bakes a fixed-matrix synthetic oblique into its own
//  UIFont (syntheticObliqueUIFont, Renderer/ComponentRenderer.swift —
//  the bundled Inter has no italic face, so `.italic()` no-ops there),
//  while the box-level TypographyApplier FontMod still uses `.italic()`
//  (real italics exist on the SF side; the label's direct `.font` wins
//  over the box font anyway, Apple docs on Text.font precedence).
//

import Foundation

enum FontStyleApplier {
    static func contribute(_ cfg: FontStyleConfig?, into agg: inout TypographyAggregate) {
        guard let italic = cfg?.italic else { return }
        agg.italic = italic
        agg.touched = true
    }
}
