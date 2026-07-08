//
//  UnsupportedRegionsConfig.swift
//  StyleEngine/regions — Phase 10.
//
//  CSS Regions (flow-into / flow-from / wrap-*) — never shipped in
//  mainstream engines; iOS has no regions layout.
//

import Foundation

struct UnsupportedRegionsConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
