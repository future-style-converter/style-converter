//
//  TabSizeExtractor.swift
//  StyleEngine/typography/spacing — Phase 6.
//

import Foundation

enum TabSizeProperty { static let name = "TabSize" }

enum TabSizeExtractor {
    static func extract(from properties: [IRProperty]) -> TabSizeConfig? {
        var cfg = TabSizeConfig()
        var touched = false
        for prop in properties where prop.type == TabSizeProperty.name {
            touched = true
            // Prefer explicit int. Length-form falls through to nil.
            cfg.count = ValueExtractors.extractInt(prop.data)
        }
        return touched ? cfg : nil
    }
}
