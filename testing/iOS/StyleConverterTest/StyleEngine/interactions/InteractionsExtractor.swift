//
//  InteractionsExtractor.swift
//  StyleEngine/interactions — Phase 10.
//

import Foundation

enum InteractionsProperty {
    /// 8 entries. Note: ScrollBehavior historically lives in this parser
    /// folder but is claimed by `ScrollingProperty` (Phase 10 scrolling).
    static let names: [String] = [
        "PointerEvents", "TouchAction", "UserSelect",
        "Cursor", "Resize", "Interactivity",
        "Caret", "CaretShape",
    ]
    static var set: Set<String> { Set(names) }
}

enum InteractionsExtractor {
    static func extract(from properties: [IRProperty]) -> InteractionsConfig? {
        var cfg = InteractionsConfig()
        let owned = InteractionsProperty.set
        for p in properties where owned.contains(p.type) {
            cfg.touched = true
            if let kw = ValueExtractors.extractKeyword(p.data) {
                cfg.rawByType[p.type] = kw
            } else {
                cfg.rawByType[p.type] = String(describing: p.data)
            }
        }
        return cfg.touched ? cfg : nil
    }
}
