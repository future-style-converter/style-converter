//
//  CaretColorExtractor.swift
//  StyleEngine/color — Phase 4.
//
//  Extracts CSS `caret-color`. The IR shape matches any colour-valued
//  property — either a legacy static color ({srgb:{..}, original}) or a
//  dynamic kind (color-mix/light-dark/var()). We defer all colour parsing
//  to Phase 1's `extractColor(_:)`.
//

import Foundation

enum CaretColorProperty {
    static let names: [String] = ["CaretColor"]
}

enum CaretColorExtractor {

    // Single-pass extractor. Returns nil when no CaretColor entry exists.
    static func extract(from properties: [IRProperty]) -> CaretColorConfig? {
        var color: ColorValue? = nil

        // Last-wins.
        for prop in properties where prop.type == "CaretColor" {
            // Phase 1 entry point — handles every colour flavour.
            color = extractColor(prop.data)
        }

        // Nil means "property not in IR"; the applier short-circuits.
        guard let c = color else { return nil }
        return CaretColorConfig(color: c)
    }
}
