//
//  GlobalExtractor.swift
//  StyleEngine/global — Phase 10.
//

import Foundation

enum GlobalProperty {
    static let names: [String] = ["All"]
    static var set: Set<String> { Set(names) }
}

enum GlobalExtractor {
    static func extract(from properties: [IRProperty]) -> GlobalConfig? {
        var cfg = GlobalConfig()
        let owned = GlobalProperty.set
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

    /// Wave 18 — the `all: <global>` sole-override drop (the consumption
    /// point Compose's ComponentRenderer.allReset established): CSS
    /// `all: initial|inherit|unset|revert|revert-layer` resets every
    /// other property to its respective global value (css-cascade-4 §3.2).
    /// A per-property reset cannot be synthesized at runtime, but for the
    /// audit-pinned common case — `all` overriding an isolated element —
    /// the faithful observable behaviour is to DROP every other
    /// declaration so the element collapses to its untouched defaults
    /// (web renders exactly that for the All_* audit fixtures). Any
    /// recognizable keyword triggers the drop, mirroring Compose's
    /// "firstOrNull + primitive keyword" test byte-for-byte. Identity
    /// (the same array) for every All-free list — the whole existing
    /// corpus takes the fast path. Pure — pinned in ContentsUnboxingTests.
    static func applyingAllReset(to properties: [IRProperty]) -> [IRProperty] {
        let hasAllKeyword = properties.contains { p in
            p.type == "All" && ValueExtractors.extractKeyword(p.data) != nil
        }
        // The reset drops EVERYTHING (the `All` entry included — its
        // GlobalConfig applier is a no-op anyway), exactly like Compose's
        // `unresolvedProperties = emptyList()` branch.
        return hasAllKeyword ? [] : properties
    }
}
