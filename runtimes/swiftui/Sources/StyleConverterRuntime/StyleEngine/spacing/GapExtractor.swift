//
//  GapExtractor.swift
//  StyleEngine/spacing — Phase 2.
//
//  Reads RowGap / ColumnGap / Gap (the last expands to both rows+cols)
//  out of a property list into a GapConfig. Note that the Kotlin
//  converter already expands the shorthand, so we rarely see bare "Gap"
//  in practice — but we still handle it defensively.
//

// Foundation only.
import Foundation

enum GapProperty {
    // The renderer treats `Gap` itself as migrated because the shorthand
    // path occasionally keeps the original name in hand-authored IR.
    static let names: [String] = ["Gap", "RowGap", "ColumnGap"]
}

enum GapExtractor {

    // Single-pass extractor. Gap uses the standard `extractLength` (not
    // percent-default) because the gap IR wraps length objects with a
    // type tag — bare numerics aren't a recognised gap shape.
    static func extract(from properties: [IRProperty]) -> GapConfig? {
        var cfg = GapConfig()
        var touched = false

        for prop in properties {
            switch prop.type {
            case "Gap":
                // Shorthand: both axes share the value.
                let v = extractLength(prop.data)
                cfg.row = v
                cfg.column = v
                touched = true
            case "RowGap":
                cfg.row = extractLength(prop.data); touched = true
            case "ColumnGap":
                cfg.column = extractLength(prop.data); touched = true
            default:
                break
            }
        }

        return touched ? cfg : nil
    }
}
