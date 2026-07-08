//
//  ContentConfig.swift
//  StyleEngine/content — Phase 10.
//
//  The CSS `content` property — mostly meaningful on ::before/::after
//  which SwiftUI doesn't model. Identity.
//
//  NOTE: Quotes is Phase 6 typography; not claimed here.
//

import Foundation

struct ContentConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
