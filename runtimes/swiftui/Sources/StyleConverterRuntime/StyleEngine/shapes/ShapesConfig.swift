//
//  ShapesConfig.swift
//  StyleEngine/shapes — Phase 10.
//
//  shape-outside / shape-margin / shape-padding / shape-image-
//  threshold / shape-inside. These only have meaning when an element
//  floats — SwiftUI has no float/flow-around model, so identity.
//

import Foundation

struct ShapesConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
