//
//  UnsupportedNavigationConfig.swift
//  StyleEngine/navigation — Phase 10.
//
//  CSS-nav-{up,down,left,right} + reading-order. TV/pointer-focus
//  directional navigation spec, never broadly shipped; no SwiftUI
//  analog (focus engine uses a different primitive).
//

import Foundation

struct UnsupportedNavigationConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
