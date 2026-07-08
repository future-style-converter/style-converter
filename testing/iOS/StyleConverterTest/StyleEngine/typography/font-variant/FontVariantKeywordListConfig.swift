//
//  FontVariantKeywordListConfig.swift
//  StyleEngine/typography/font-variant — Phase 6.
//
//  Shared Config struct for the six `font-variant-*` properties whose
//  SwiftUI support is identical (i.e. none): `-numeric`, `-ligatures`,
//  `-east-asian`, `-position`, `-alternates`, `-emoji`. Each of these
//  still gets its own Extractor + Applier triplet per the per-property
//  contract — but they all carry the same shape of data (a list of
//  keyword tokens) so they share the Config type to avoid mechanical
//  duplication. Extractors are one-liners over this type; Appliers are
//  identity with TODOs pointing at UIFontDescriptor feature tags.
//

import Foundation

/// Ordered keyword tokens as declared in CSS. Empty when the value was
/// `normal` or `none`. Preserved for audit + future UIFontDescriptor routing.
struct FontVariantKeywordListConfig: Equatable {
    /// Declared keywords; lower-cased.
    var keywords: [String] = []
}

// MARK: - Shared helper used by each font-variant-* extractor below.

// Internal parser: accepts either a string, a {keyword:…} object, an array
// of either, or a space-separated string; emits lower-cased keyword list.
enum FontVariantKeywordParse {
    static func parse(_ value: IRValue) -> [String] {
        // Bare string — e.g. "normal" or "tabular-nums lining-nums".
        if let s = value.stringValue { return s.lowercased().split(separator: " ").map(String.init) }
        // { keyword: "..." }.
        if case .object(let o) = value, let kw = o["keyword"]?.stringValue {
            return [kw.lowercased()]
        }
        // Array of strings or {keyword} objects.
        if case .array(let entries) = value {
            return entries.compactMap { e -> String? in
                if let s = e.stringValue { return s.lowercased() }
                if case .object(let o) = e { return o["keyword"]?.stringValue?.lowercased() }
                return nil
            }
        }
        return []
    }
}
