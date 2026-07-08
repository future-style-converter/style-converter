//
//  KeywordValue.swift
//  StyleConverterTest — StyleEngine/core/types
//
//  Normalised keyword wrapper. Keywords arrive in multiple shapes across
//  the IR (bare strings, `{keyword:...}`, `{value:...}`, `{type:...}`). We
//  collapse them to a single lower-kebab-case string so switches are short
//  and consistent.
//

import Foundation

// Equatable — enables direct `==` tests against a literal, plus the helper
// matches(...) for multi-way dispatch without building sets.
struct KeywordValue: Equatable {
    let normalized: String   // Lowercase, hyphen-separated. Never nil, never empty.

    // Sugar for `switch` alternatives: `kw.matches("center","flex-start")`.
    func matches(_ candidates: String...) -> Bool {
        candidates.contains(normalized)
    }
}

// Extract a keyword from any of the known shapes. Returns nil when no
// string-like field is present — callers decide whether that's a problem.
func extractKeyword(_ value: IRValue?) -> KeywordValue? {
    guard let value = value else { return nil }

    switch value {
    case .string(let s):
        return KeywordValue(normalized: normalize(s))
    case .object(let o):
        // `keyword` is preferred; `value`/`type` are historical fallbacks.
        if let s = (o["keyword"] ?? o["value"] ?? o["type"])?.stringValue {
            return KeywordValue(normalized: normalize(s))
        }
        return nil
    default:
        return nil
    }
}

// Canonical form: lowercase + hyphen-delimited. Underscores, spaces, and
// uppercase all normalise to the same output so Kotlin enum names
// ("MIN_CONTENT") and CSS literals ("min-content") compare equal.
private func normalize(_ s: String) -> String {
    s.lowercased()
     .replacingOccurrences(of: "_", with: "-")
     .replacingOccurrences(of: " ", with: "-")
}
