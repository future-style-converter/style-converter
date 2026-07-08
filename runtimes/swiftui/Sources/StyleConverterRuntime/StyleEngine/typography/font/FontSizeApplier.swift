//
//  FontSizeApplier.swift
//  StyleEngine/typography/font — Phase 6.
//
//  Contributes the extracted FontSize into the shared TypographyAggregate.
//  The aggregate is consumed once by TypographyApplier so font size +
//  weight + style + family can be collapsed into a single `.font(...)`
//  call — see TypographyAggregate.swift for the rationale.
//

// SwiftUI is only needed via TypographyAggregate; Foundation is enough.
import Foundation

enum FontSizeApplier {
    /// Pure reducer: write the extracted size into the aggregate.
    /// No-op when cfg is nil — lets StyleBuilder call this unconditionally.
    static func contribute(_ cfg: FontSizeConfig?, into agg: inout TypographyAggregate) {
        // Only mutate on presence — otherwise we'd overwrite a value a
        // sibling applier legitimately left nil.
        guard let cfg = cfg, let px = cfg.px else { return }
        agg.fontSizePx = px
        // Tag the aggregate so TypographyApplier knows to emit modifiers.
        agg.touched = true
    }
}
