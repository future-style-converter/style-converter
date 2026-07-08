//
//  InteractionsApplier.swift
//  StyleEngine/interactions — Phase 10.
//
//  Identity for now. Partial applicability notes (not wired this phase):
//    • PointerEvents == "none"   → `.allowsHitTesting(false)`
//    • UserSelect == "none"      → `.textSelection(.disabled)` on iOS 15+
//    • Cursor                    → iPadOS pointer API is per-view but has
//      a narrower keyword set than CSS and would require an opt-in
//      mapping table. No API on iPhone.
//    • TouchAction               → SwiftUI exposes simultaneous/high-
//      priority gestures but not CSS's axis-level manipulation policy.
//    • Resize / Interactivity    → no analog.
//    • Caret / CaretShape        → iOS tint-colour only; CSS's shape
//      palette (bar, block, underscore) is unsupported.
//
//  TODO(phase-11): wire PointerEvents=none + UserSelect=none through
//  the applyStyle chain once we have a stable "interaction-gate"
//  modifier position (after transforms, before visibility).
//

import Foundation

enum InteractionsApplier {
    static func contribute(_ cfg: InteractionsConfig?) { _ = cfg }
}
