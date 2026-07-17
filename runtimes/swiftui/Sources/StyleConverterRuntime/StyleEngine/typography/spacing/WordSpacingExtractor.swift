//
//  WordSpacingExtractor.swift
//  StyleEngine/typography/spacing — Phase 6.
//

import Foundation

enum WordSpacingProperty { static let name = "WordSpacing" }

enum WordSpacingExtractor {
    static func extract(from properties: [IRProperty]) -> WordSpacingConfig? {
        var cfg = WordSpacingConfig()
        var touched = false
        for prop in properties where prop.type == WordSpacingProperty.name {
            touched = true
            // `normal` keyword → no override. The LIVE wire spells it
            // `{"px":0.0,"original":"normal"}` (WordSpacingSerializer.kt)
            // — the keyword rides `original`, which extractKeyword does
            // not read, so without the second check the bogus px 0.0 was
            // adopted as a declared zero spacing (lane IOS-TEXT fix 2/3).
            if ValueExtractors.extractKeyword(prop.data)?.lowercased() == "normal"
                || isNormalOriginal(prop.data) {
                cfg.px = nil; cfg.relative = nil; continue
            }
            // Lane IOS-TEXT fix 3 — same bogus-px-0 relative wire shape
            // as letter-spacing (RelativeFontLength.parse); must run
            // before extractPx or the 0 wins and the spacing vanishes.
            if let rel = RelativeFontLength.parse(prop.data) {
                cfg.relative = rel; cfg.px = nil; continue
            }
            cfg.px = ValueExtractors.extractPx(prop.data)
            cfg.relative = nil   // a later absolute value overrides (cascade)
        }
        return touched ? cfg : nil
    }

    /// True for the live `normal` wire shape `{"px":0.0,"original":"normal"}`
    /// — the serializer keeps the keyword in `original` only.
    private static func isNormalOriginal(_ data: IRValue) -> Bool {
        guard case .object(let o) = data else { return false }
        return o["original"]?.stringValue?.lowercased() == "normal"
    }
}
