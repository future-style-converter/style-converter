//
//  ListsConfig.swift
//  StyleEngine/lists — Phase 10.
//
//  list-style-type / -image / -position. SwiftUI List uses system
//  styling — you cannot set arbitrary bullet glyphs on custom
//  containers; identity for now.
//
//  NOTE: `Quotes` is Phase 6 typography — not claimed here.
//

import Foundation

struct ListsConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
