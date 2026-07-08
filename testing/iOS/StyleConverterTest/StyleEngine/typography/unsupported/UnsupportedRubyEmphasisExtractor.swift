//
//  UnsupportedRubyEmphasisExtractor.swift
//  StyleEngine/typography/unsupported — Phase 6.
//
//  Walks the property list once and captures every owned property into
//  the shared Config map. Each property name is still registered with
//  PropertyRegistry so coverage tooling sees it routed through the
//  engine.
//

import Foundation

enum UnsupportedRubyEmphasisProperty {
    /// Full list of IR property type names this grouped extractor owns.
    /// Kept in a single place so PropertyRegistry can import it verbatim.
    static let names: [String] = [
        "RubyAlign",
        "RubyMerge",
        "RubyPosition",
        "RubyOverhang",
        "TextEmphasis",
        "TextEmphasisStyle",
        "TextEmphasisColor",
        "TextEmphasisPosition",
        "TextCombineUpright",
        "HangingPunctuation"
    ]
    static var set: Set<String> { Set(names) }
}

enum UnsupportedRubyEmphasisExtractor {
    static func extract(from properties: [IRProperty]) -> UnsupportedRubyEmphasisConfig? {
        var cfg = UnsupportedRubyEmphasisConfig()
        let owned = UnsupportedRubyEmphasisProperty.set
        for prop in properties where owned.contains(prop.type) {
            // Keep a stringified representation for the audit. Keywords land
            // here verbatim; other IR shapes get debug-described so tooling
            // can still point at the value source.
            cfg.touched = true
            if let kw = ValueExtractors.extractKeyword(prop.data) {
                cfg.rawByType[prop.type] = kw
            } else {
                cfg.rawByType[prop.type] = String(describing: prop.data)
            }
        }
        return cfg.touched ? cfg : nil
    }
}
