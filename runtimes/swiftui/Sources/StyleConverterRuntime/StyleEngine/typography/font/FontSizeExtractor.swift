//
//  FontSizeExtractor.swift
//  StyleEngine/typography/font — Phase 6.
//
//  Pulls `FontSize` out of the property list and returns a FontSizeConfig.
//  The CSS parser delivers absolute keyword → length resolution upstream
//  so we only need to unwrap the `{ "px": N }` blob here. See
//  ValueExtractors.extractPx for the exact unwrap contract.
//

// Foundation is sufficient; no SwiftUI needed at extract time.
import Foundation

// Property-type name owned by this extractor. Registered with
// PropertyRegistry so the legacy StyleBuilder switch skips it.
enum FontSizeProperty { static let name = "FontSize" }

enum FontSizeExtractor {

    /// Linear scan of `properties` looking for `FontSize`. Later
    /// occurrences win — matches the CSS last-wins cascade within a
    /// single rule. Returns nil when no `FontSize` was present.
    static func extract(from properties: [IRProperty]) -> FontSizeConfig? {
        // Default config; only stamped when a FontSize entry is found.
        var cfg = FontSizeConfig()
        var touched = false
        for prop in properties where prop.type == FontSizeProperty.name {
            // extractPx accepts {px:N}, raw numbers, and ignores keyword
            // residues — perfect for this prop.
            cfg.px = ValueExtractors.extractPx(prop.data)
            touched = true
        }
        return touched ? cfg : nil
    }
}
