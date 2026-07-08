//
//  UnsupportedPrintConfig.swift
//  StyleEngine/print — Phase 10.
//
//  CSS @page + print-bookmark + print-footnote + marks/bleed family.
//  iOS AirPrint is driven at UIPrintInteractionController level, not
//  from CSS — nothing here has a SwiftUI analog.
//

import Foundation

struct UnsupportedPrintConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
