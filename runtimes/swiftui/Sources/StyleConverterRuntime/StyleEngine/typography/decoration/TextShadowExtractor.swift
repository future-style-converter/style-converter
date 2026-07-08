//
//  TextShadowExtractor.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextShadowProperty { static let name = "TextShadow" }

enum TextShadowExtractor {
    static func extract(from properties: [IRProperty]) -> TextShadowConfig? {
        var cfg = TextShadowConfig()
        var touched = false
        for prop in properties where prop.type == TextShadowProperty.name {
            touched = true
            cfg.layers = []
            // `none` keyword short-circuits to empty layer list.
            if ValueExtractors.extractKeyword(prop.data)?.lowercased() == "none" { continue }
            // Array of { x, y, blur?, c? } objects.
            if case .array(let entries) = prop.data {
                for entry in entries {
                    guard case .object(let o) = entry else { continue }
                    let x = ValueExtractors.extractPx(o["x"] ?? .null) ?? 0
                    let y = ValueExtractors.extractPx(o["y"] ?? .null) ?? 0
                    let blur = ValueExtractors.extractPx(o["blur"] ?? .null) ?? 0
                    let color = ValueExtractors.extractColor(o["c"] ?? .null)
                    cfg.layers.append(TextShadowLayer(x: x, y: y, radius: blur, color: color))
                }
            }
        }
        return touched ? cfg : nil
    }
}
