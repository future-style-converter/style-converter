//
//  ColorExtractor.swift
//  StyleEngine/color — Phase 4.
//
//  Pulls `BackgroundColor` and `Color` out of a property list and returns
//  a ColorConfig. The heavy lifting lives in `extractColor(_:)` from
//  Phase 1 (ColorValue.swift) — this extractor is a thin routing layer
//  so the registry can migrate colours on the same cadence as the other
//  property families.
//

// Foundation only — no SwiftUI needed at extraction time.
import Foundation

// Sibling list of property-type names this extractor owns. The registry
// consults this (via PropertyRegistry.migrated) to route each IR entry.
enum ColorProperty {
    // CSS `background-color` → paints the component box.
    // CSS `color` → the text foreground + the `currentColor` resolution
    // target for other colour-valued properties.
    static let names: [String] = ["BackgroundColor", "Color"]
}

// Single-pass extractor. Iterates the property list once and populates a
// ColorConfig. Returns nil when neither property was present, mirroring
// GapExtractor's contract so the applier can skip cleanly.
enum ColorExtractor {

    // Walk the properties, dispatching on `prop.type`. Later occurrences
    // win if the IR somehow contains duplicates — matches CSS's last-wins
    // cascade inside a single rule.
    static func extract(from properties: [IRProperty]) -> ColorConfig? {
        // Start empty; flag any touch so we can differentiate "absent" from
        // "present but resolved to .unknown".
        var cfg = ColorConfig()
        var touched = false

        // Linear scan — property lists are short (<50 entries typical).
        for prop in properties {
            switch prop.type {
            case "BackgroundColor":
                // Phase 1 extractor never throws / never returns nil. We
                // keep the ColorValue verbatim so the applier can decide
                // how to handle `.unknown` / `.dynamic` fallbacks.
                cfg.background = extractColor(prop.data)
                touched = true
            case "Color":
                // Text foreground — same deal. The text renderer in
                // ComponentRenderer reads `style.text.color`; StyleBuilder
                // still mirrors this value there for compatibility.
                cfg.foreground = extractColor(prop.data)
                touched = true
            default:
                // Not ours — every other property flows through its own
                // extractor (or the legacy switch for non-migrated ones).
                break
            }
        }

        // `nil` signals "no ColorApplier modifier needed" to StyleBuilder.
        return touched ? cfg : nil
    }
}
