//
//  FontFamilyExtractor.swift
//  StyleEngine/typography/font — Phase 6.
//
//  The IR blob for `font-family` is either an array of
//  `{ "name": "..." }` / `{ "keyword": "serif" }` objects or a single
//  string (shortcut when there was one unquoted family name).
//

import Foundation

enum FontFamilyProperty { static let name = "FontFamily" }

enum FontFamilyExtractor {
    static func extract(from properties: [IRProperty]) -> FontFamilyConfig? {
        // Capture the last FontFamily wins (CSS cascade).
        var cfg = FontFamilyConfig()
        var touched = false
        for prop in properties where prop.type == FontFamilyProperty.name {
            touched = true
            cfg = FontFamilyConfig()
            // Case 1: array of entries — the common output of the parser.
            if case .array(let entries) = prop.data {
                for entry in entries {
                    // An entry can be an object {"name":"Helvetica"} or
                    // {"keyword":"serif"} or a bare string.
                    if case .object(let o) = entry {
                        if let n = o["name"]?.stringValue { cfg.names.append(n) }
                        if let kw = o["keyword"]?.stringValue {
                            classifyGeneric(kw, into: &cfg)
                        }
                    } else if let s = entry.stringValue {
                        // Bare string: either a name or a generic keyword.
                        classifyGeneric(s, into: &cfg)
                        if !isGeneric(s) { cfg.names.append(s) }
                    }
                }
            }
            // Case 2: single string blob.
            else if let s = prop.data.stringValue {
                classifyGeneric(s, into: &cfg)
                if !isGeneric(s) { cfg.names.append(s) }
            }
        }
        return touched ? cfg : nil
    }

    // Mark known generic family keywords so the applier can pick the right
    // SwiftUI Font.Design even when no real face name matches.
    private static func classifyGeneric(_ raw: String, into cfg: inout FontFamilyConfig) {
        switch raw.lowercased() {
        case "monospace", "ui-monospace": cfg.hasMonospace = true
        case "serif", "ui-serif":          cfg.hasSerif = true
        case "ui-rounded":                 cfg.hasRounded = true
        default: break   // other generics (cursive/fantasy/emoji) fall through
        }
    }

    // Returns true when `raw` is one of the CSS generic family keywords
    // and should not be added to `names` as a face name.
    private static func isGeneric(_ raw: String) -> Bool {
        switch raw.lowercased() {
        case "monospace", "serif", "sans-serif", "cursive", "fantasy",
             "system-ui", "ui-serif", "ui-sans-serif", "ui-monospace",
             "ui-rounded", "emoji", "math", "fangsong":
            return true
        default:
            return false
        }
    }
}
