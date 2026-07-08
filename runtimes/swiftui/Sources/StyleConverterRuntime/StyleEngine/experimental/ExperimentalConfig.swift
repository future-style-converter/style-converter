//
//  ExperimentalConfig.swift
//  StyleEngine/experimental — Phase 10.
//
//  presentation-level, running, string-set. Experimental/editor's-
//  draft properties with no shipping rendering contract.
//

import Foundation

struct ExperimentalConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
