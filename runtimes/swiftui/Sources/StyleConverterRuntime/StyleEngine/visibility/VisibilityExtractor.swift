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
        // Logical sides — resolve last with LTR mapping: block → Y,
        // inline → X. Physical longhands (OverflowX/Y) override logical.
        var logicalX: OverflowKind? = nil
        var logicalY: OverflowKind? = nil

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
                if let k = mapOverflow(p.data.stringValue) { logicalY = k; cfg.touched = true }
            case "OverflowInline":
                if let k = mapOverflow(p.data.stringValue) { logicalX = k; cfg.touched = true }
            default: break
            }
        }

        // Fold logical → physical if the physical axis wasn't set.
        if cfg.overflowX == nil, let lx = logicalX { cfg.overflowX = lx }
        if cfg.overflowY == nil, let ly = logicalY { cfg.overflowY = ly }

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
