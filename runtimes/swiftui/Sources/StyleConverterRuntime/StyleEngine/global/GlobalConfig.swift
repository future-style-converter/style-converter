//
//  GlobalConfig.swift
//  StyleEngine/global — Phase 10.
//
//  The `all` shorthand. Reset-all semantics require property-by-
//  property resolution (initial / inherit / unset / revert / revert-
//  layer) against the parent computed style, which the SDUI runtime
//  doesn't maintain. Identity until that machinery exists.
//

import Foundation

struct GlobalConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
