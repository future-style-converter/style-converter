//
//  RhythmConfig.swift
//  StyleEngine/rhythm — Phase 10.
//
//  block-step family (block-step, block-step-align, block-step-insert,
//  block-step-round, block-step-size). Baseline-rhythm helpers with
//  no SwiftUI analog. Note: line-grid / line-snap / line-height-step
//  were already claimed by Phase 6 typography.
//

import Foundation

struct RhythmConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
