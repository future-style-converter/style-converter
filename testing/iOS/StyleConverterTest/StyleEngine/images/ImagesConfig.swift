//
//  ImagesConfig.swift
//  StyleEngine/images — Phase 10.
//
//  image-rendering, object-fit, object-position, object-view-box.
//  Identity in this phase — SwiftUI ContentMode translates object-fit
//  reasonably but is an Image-level parameter, not a modifier, and
//  the SDUI runtime doesn't surface that hook yet.
//

import Foundation

struct ImagesConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
