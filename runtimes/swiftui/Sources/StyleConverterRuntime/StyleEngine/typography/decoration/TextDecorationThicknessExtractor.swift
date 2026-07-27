//
//  TextDecorationThicknessExtractor.swift
//  StyleEngine/typography/decoration — Phase 6.
//

import Foundation

enum TextDecorationThicknessProperty { static let name = "TextDecorationThickness" }

enum TextDecorationThicknessExtractor {
    static func extract(from properties: [IRProperty]) -> TextDecorationThicknessConfig? {
        var cfg = TextDecorationThicknessConfig()
        var touched = false
        for prop in properties where prop.type == TextDecorationThicknessProperty.name {
            touched = true
            // `auto` / `from-font` keywords → nil (platform default).
            // Wave 21 (lane TEXTDECOR, B-RC8): match the KEYWORD VALUE,
            // not mere keyword presence — the live converter wire for a
            // length is {"type":"length","px":N} (per-test IR:
            // wave21-gate css-text-decor text-decoration-dotted-001,
            // px 10/20/30) and extractKeyword happily returns its
            // "type" discriminator ("length"), so the old nil-out
            // branch silently DROPPED every explicit thickness — the
            // exact lossy:false loss this wave exists to kill.
            if let kw = ValueExtractors.extractKeyword(prop.data)?.lowercased(),
               kw == "auto" || kw == "from-font" || kw == "from_font" {
                cfg.px = nil
                continue
            }
            // Length wire → device px ({"px":N} envelope or bare number).
            cfg.px = ValueExtractors.extractPx(prop.data)
        }
        return touched ? cfg : nil
    }
}
