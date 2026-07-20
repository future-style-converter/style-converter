//
//  VisibilityExtractor.swift
//  StyleEngine/visibility — Phase 8.
//

import Foundation

enum VisibilityProperty {
    // Centralised list so PropertyRegistry + self-test stay in sync.
    static let set: Set<String> = [
        "Visibility", "Overflow",
        "OverflowX", "OverflowY", "OverflowBlock", "OverflowInline",
    ]
}

enum VisibilityExtractor {

    static func extract(from properties: [IRProperty]) -> VisibilityConfig? {
        var cfg = VisibilityConfig()
        // Wave-18 cleanup (lane-3 skeptic): logical longhands write their
        // PHYSICAL axis directly, in declaration order — CSS resolves
        // same-specificity declarations by order (CSS Cascade §"Order of
        // Appearance"), and css-logical §4 maps overflow-block → Y,
        // overflow-inline → X under horizontal-tb (the only writing mode
        // this renderer supports). The old two-pass fold gave physical
        // longhands unconditional priority, so the live wire
        // [OverflowY:"HIDDEN", OverflowBlock:"VISIBLE"] clipped on iOS
        // while Compose (OverflowExtractor.kt, last-write-wins) and web
        // left it visible. Byte-parallel with the Compose loop now.

        for p in properties {
            switch p.type {
            case "Visibility":
                // UPPERCASE keyword — map explicitly to avoid the `.none`
                // Optional pitfall if the enum ever gains a `.none` case.
                switch p.data.stringValue {
                case "VISIBLE":  cfg.visibility = VisibilityKind.visible
                case "HIDDEN":   cfg.visibility = VisibilityKind.hidden
                case "COLLAPSE": cfg.visibility = VisibilityKind.collapse
                default: break
                }
                cfg.touched = true
            case "Overflow":
                // `overflow` shorthand — CSS expands to x + y longhands,
                // but the parser also emits a direct bare-keyword form
                // when both axes are equal. Cover both.
                if let k = mapOverflow(p.data.stringValue) {
                    cfg.overflowX = k; cfg.overflowY = k
                    cfg.touched = true
                }
            case "OverflowX":
                if let k = mapOverflow(p.data.stringValue) { cfg.overflowX = k; cfg.touched = true }
            case "OverflowY":
                if let k = mapOverflow(p.data.stringValue) { cfg.overflowY = k; cfg.touched = true }
            case "OverflowBlock":
                // Block axis = Y in horizontal-tb; a later OverflowY (or a
                // later OverflowBlock) overwrites — cascade order.
                if let k = mapOverflow(p.data.stringValue) { cfg.overflowY = k; cfg.touched = true }
            case "OverflowInline":
                // Inline axis = X in horizontal-tb; same last-write rule.
                if let k = mapOverflow(p.data.stringValue) { cfg.overflowX = k; cfg.touched = true }
            default: break
            }
        }

        return cfg.touched ? cfg : nil
    }

    // UPPERCASE keyword → typed enum. Returns nil on unknown.
    private static func mapOverflow(_ s: String?) -> OverflowKind? {
        switch s {
        case "VISIBLE": return .visible
        case "HIDDEN":  return .hidden
        case "CLIP":    return .clip
        case "SCROLL":  return .scroll
        case "AUTO":    return .auto
        default:        return nil
        }
    }
}
