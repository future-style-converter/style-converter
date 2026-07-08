//
//  InteractionsConfig.swift
//  StyleEngine/interactions — Phase 10.
//
//  Input/pointer hints: pointer-events, touch-action, user-select,
//  cursor, resize, interactivity, caret, caret-shape. Limited iOS
//  analog: `.allowsHitTesting(false)` ≈ pointer-events:none,
//  `.textSelection(.disabled)` ≈ user-select:none. The rest are
//  identity with TODO.
//

import Foundation

struct InteractionsConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
