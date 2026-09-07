//
//  InteractionsConfig.swift
//  StyleEngine/interactions — Phase 10.
//
//  Input/pointer hints: pointer-events, touch-action, user-select,
//  cursor, resize, interactivity, caret, caret-shape. NOTHING is applied
//  (retro P2b deleted the identity applier, A6#10 — it had no caller);
//  the per-property analysis it carried, which is the useful half:
//    • PointerEvents == "none"  → `.allowsHitTesting(false)`
//    • UserSelect  == "none"    → `.textSelection(.disabled)` (iOS 15+)
//    • Cursor                   → iPadOS pointer API is per-view but has a
//      narrower keyword set than CSS; no API on iPhone
//    • TouchAction              → SwiftUI has simultaneous/high-priority
//      gestures, not CSS's axis-level manipulation policy
//    • Resize / Interactivity   → no analogue
//    • Caret / CaretShape       → tint colour only; CSS's bar/block/
//      underscore palette is unsupported
//  Wiring the first two needs a stable interaction-gate position in the
//  modifier chain (after transforms, before visibility).
//

import Foundation

struct InteractionsConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
