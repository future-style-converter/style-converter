//
//  VerticalAlignExtractor.swift
//  StyleEngine/typography/writing — Phase 6.
//

import Foundation

enum VerticalAlignProperty { static let name = "VerticalAlign" }

enum VerticalAlignExtractor {
    static func extract(from properties: [IRProperty]) -> VerticalAlignConfig? {
        var cfg = VerticalAlignConfig()
        var touched = false
        for prop in properties where prop.type == VerticalAlignProperty.name {
            touched = true
            if let kw = ValueExtractors.extractKeyword(prop.data)?.lowercased() {
                // Coarse but conservative mapping — matches most CSS UAs.
                switch kw {
                case "super":    cfg.offsetPx = 4
                case "sub":      cfg.offsetPx = -4
                case "top":      cfg.offsetPx = 8
                case "bottom":   cfg.offsetPx = -8
                case "middle":   cfg.offsetPx = 0
                case "baseline": cfg.offsetPx = 0
                default:         cfg.offsetPx = nil
                }
                continue
            }
            cfg.offsetPx = ValueExtractors.extractPx(prop.data)
        }
        return touched ? cfg : nil
    }
}
