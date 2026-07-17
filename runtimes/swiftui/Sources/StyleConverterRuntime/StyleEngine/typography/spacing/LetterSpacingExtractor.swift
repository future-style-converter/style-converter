//
//  LetterSpacingExtractor.swift
//  StyleEngine/typography/spacing — Phase 6.
//

import Foundation

enum LetterSpacingProperty { static let name = "LetterSpacing" }

enum LetterSpacingExtractor {
    static func extract(from properties: [IRProperty]) -> LetterSpacingConfig? {
        var cfg = LetterSpacingConfig()
        var touched = false
        for prop in properties where prop.type == LetterSpacingProperty.name {
            touched = true
            // `normal` keyword → no override. Lengths go through extractPx.
            // The LIVE wire spells the keyword `{"px":0.0,"original":
            // "normal"}` (LetterSpacingSerializer.kt) — extractKeyword
            // never reads `original`, so the bogus px 0.0 used to be
            // adopted as a declared zero tracking (lane IOS-TEXT fix 3).
            if ValueExtractors.extractKeyword(prop.data)?.lowercased() == "normal"
                || isNormalOriginal(prop.data) {
                cfg.px = nil; cfg.relative = nil; continue
            }
            // Lane IOS-TEXT fix 3 — em/rem tracking ships with a bogus
            // `px: 0.0` and the true value at original.original.{v,u}
            // (see RelativeFontLength.parse). Detect it BEFORE extractPx,
            // which would happily adopt the 0 and erase the tracking
            // (the Compose Typography_C10 failure mode, mirrored).
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
