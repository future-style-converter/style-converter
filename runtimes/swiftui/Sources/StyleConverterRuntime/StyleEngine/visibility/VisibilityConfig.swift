//
//  VisibilityConfig.swift
//  StyleEngine/visibility — Phase 8.
//
//  Combines CSS `visibility` + the four axis-specific overflow longhands
//  (`overflow-x` / `overflow-y` / `overflow-block` / `overflow-inline`).
//  SwiftUI exposes `.hidden()`, `.clipped()` and `ScrollView` for these;
//  the applier picks the right combination based on the kind field.
//

import Foundation

// CSS `visibility`.
enum VisibilityKind: String, Equatable {
    case visible, hidden, collapse
}

// CSS `overflow-*` keyword set — shared across all four longhands.
enum OverflowKind: String, Equatable {
    case visible, hidden, clip, scroll, auto
}

struct VisibilityConfig: Equatable {
    // `nil` means the property wasn't declared → default visible.
    var visibility: VisibilityKind? = nil
    // Per-axis overflow; CSS initial value is `visible` so nil = unset.
    // We resolve the effective x/y by folding the logical (block/inline)
    // props at extract time with a LTR assumption, then physical wins.
    var overflowX: OverflowKind? = nil
    var overflowY: OverflowKind? = nil
    // Touched flag — set by the extractor when anything was declared.
    var touched: Bool = false
}
