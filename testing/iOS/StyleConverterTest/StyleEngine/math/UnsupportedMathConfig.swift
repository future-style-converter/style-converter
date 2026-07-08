//
//  UnsupportedMathConfig.swift
//  StyleEngine/math — Phase 10.
//
//  MathML styling (math-style / math-shift / math-depth). iOS has no
//  MathML rendering surface — identity.
//

import Foundation

struct UnsupportedMathConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
