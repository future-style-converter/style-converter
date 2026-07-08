//
//  CountersConfig.swift
//  StyleEngine/counters — Phase 10.
//
//  counter-reset / counter-increment / counter-set. DOM-counter
//  semantics require pseudo-element context that SwiftUI doesn't
//  model.
//

import Foundation

struct CountersConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
