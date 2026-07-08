//
//  BorderMiscExtractor.swift
//  StyleEngine/borders/sides — Phase 5.
//
//  Extracts the three "keyword-only" border-family properties defined
//  in BorderMiscConfig.swift. Parser files:
//    - BoxDecorationBreakPropertyParser.kt (accepts "slice"|"clone")
//    - CornerShapePropertyParser.kt        (round|bevel|scoop|notch)
//    - BorderBoundaryPropertyParser.kt     (none|parent|display)
//

import Foundation

enum BorderMiscExtractor {
    // Registered with PropertyRegistry.migrated.
    static let propertyNames: [String] = [
        "BoxDecorationBreak", "CornerShape", "BorderBoundary",
    ]

    // Returns nil when no keyword was captured — applier short-circuits.
    static func extract(from properties: [IRProperty]) -> BorderMiscConfig? {
        var cfg = BorderMiscConfig()
        var touched = false

        for prop in properties {
            // All three come through as bare uppercase strings in the IR
            // because the CSS parser emits `.string(...)` directly for
            // keyword-typed properties (see parser files above).
            let kw = ValueExtractors.extractKeyword(prop.data)?.lowercased() ?? ""
            switch prop.type {
            case "BoxDecorationBreak":
                if let v = BoxDecorationBreak(rawValue: kw) {
                    cfg.decorationBreak = v; touched = true
                }
            case "CornerShape":
                if let v = CornerShapeKind(rawValue: kw) {
                    cfg.cornerShape = v; touched = true
                }
            case "BorderBoundary":
                // Accept any non-empty keyword — iOS doesn't act on it.
                if !kw.isEmpty {
                    cfg.borderBoundary = kw; touched = true
                }
            default: break
            }
        }
        return touched ? cfg : nil
    }
}
