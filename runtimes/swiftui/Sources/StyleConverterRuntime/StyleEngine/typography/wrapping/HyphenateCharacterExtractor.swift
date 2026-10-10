//
//  HyphenateCharacterExtractor.swift
//  StyleEngine/typography/wrapping — Phase 6; rewritten in wave 54 (lane L3, U2-ios).
//
//  `IRProperty → HyphenateCharacterConfig`, mirroring the reader's
//  `converter/.../longhands/typography/HyphenateCharacterPropertyParser.kt` and
//  its IR model `irmodels/properties/typography/HyphenateCharacterProperty.kt`:
//  the wire is `{"type":"auto"}` or `{"type":"string","value":"<decoded>"}`
//  (since wave 54 the reader decodes css-syntax-3 escapes and keeps `""`).
//
//  The Phase-6 reader used `ValueExtractors.extractKeyword(...)?.lowercased()`:
//  on the object shape that returns `value` before `type` — so `{type: auto}`
//  read as the keyword only by accident, `""` was indistinguishable from a
//  missing value, and a cased string would have been folded. It now reads
//  the discriminator first and keeps the string verbatim.
//

// Foundation only: the extractor is pure data plumbing.
import Foundation

/// The IR type name this triplet claims (PropertyRegistry's typography set).
enum HyphenateCharacterProperty { static let name = "HyphenateCharacter" }

/// css-text-4 §6.3 reader for one element's property list.
enum HyphenateCharacterExtractor {
    /// The element's own declaration (the LAST one wins, css-cascade-4 §6),
    /// or nil when it declares none.
    static func extract(from properties: [IRProperty]) -> HyphenateCharacterConfig? {
        // nil until a declaration is seen.
        var cfg: HyphenateCharacterConfig? = nil
        // Every declaration overwrites the previous one: last wins.
        for prop in properties where prop.type == HyphenateCharacterProperty.name {
            cfg = read(prop.data)
        }
        // nil = undeclared; the label keeps the UA hyphen.
        return cfg
    }

    /// One payload in the reader's object shape.
    private static func read(_ data: IRValue) -> HyphenateCharacterConfig {
        // The reader always emits an object with a `type` discriminator.
        guard case .object(let o) = data, let tag = o["type"]?.stringValue else {
            return unreadable()
        }
        switch tag {
        // `auto` → the UA hyphen, represented as a nil value.
        case "auto": return HyphenateCharacterConfig(value: nil)
        // `<string>` → verbatim (no trim, no case change), "" included.
        case "string":
            // A string variant without a string value is not the reader's shape.
            guard let v = o["value"]?.stringValue else { return unreadable() }
            // The painted string, exactly as decoded by the reader.
            return HyphenateCharacterConfig(value: v)
        // An unknown discriminator: never guessed at.
        default: return unreadable()
        }
    }

    /// No silent fallthrough (repo rule): one breadcrumb, then `auto` — the
    /// initial value, which is what a UA does with a declaration it cannot read.
    private static func unreadable() -> HyphenateCharacterConfig {
        // Deduped process-wide by key (PropertyTracker.logOnce).
        _ = PropertyTracker.logOnce(
            key: "hyphenate-character:unreadable",
            message: "hyphenate-character: payload is not {type: auto|string}; rendered as auto")
        // `auto` keeps the pre-wave-54 picture.
        return HyphenateCharacterConfig(value: nil)
    }
}
