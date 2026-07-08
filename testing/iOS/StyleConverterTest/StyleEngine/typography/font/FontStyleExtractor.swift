//
//  FontStyleExtractor.swift
//  StyleEngine/typography/font — Phase 6.
//
//  Keyword parse: italic|oblique → true, normal → false. Obscure oblique
//  angle syntaxes (`oblique 14deg`) land here as { keyword:"oblique" }
//  after the parser strips the angle — the CSS parser keeps the angle in
//  a sibling field we ignore on iOS (see FontStylePropertyParser.kt).
//

import Foundation

enum FontStyleProperty { static let name = "FontStyle" }

enum FontStyleExtractor {
    static func extract(from properties: [IRProperty]) -> FontStyleConfig? {
        var cfg = FontStyleConfig()
        var touched = false
        for prop in properties where prop.type == FontStyleProperty.name {
            touched = true
            // Strings or {keyword:…} blobs both come through extractKeyword.
            let kw = ValueExtractors.extractKeyword(prop.data)?.lowercased() ?? ""
            cfg.italic = (kw == "italic" || kw == "oblique")
        }
        return touched ? cfg : nil
    }
}
