//
//  DirectionExtractor.swift
//  StyleEngine/typography/writing — Phase 6.
//

import SwiftUI

enum DirectionProperty { static let name = "Direction" }

enum DirectionExtractor {
    static func extract(from properties: [IRProperty]) -> DirectionConfig? {
        var cfg = DirectionConfig()
        var touched = false
        for prop in properties where prop.type == DirectionProperty.name {
            touched = true
            switch ValueExtractors.extractKeyword(prop.data)?.lowercased() {
            case "rtl": cfg.direction = .rightToLeft
            case "ltr": cfg.direction = .leftToRight
            default:    cfg.direction = nil
            }
        }
        return touched ? cfg : nil
    }
}
