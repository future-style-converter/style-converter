//
//  RenderingConfig.swift
//  StyleEngine/rendering — Phase 10.
//
//  Render-quality hints + color-space hints + zoom/interpolate-size.
//  SwiftUI can loosely honour image-rendering and zoom; the rest are
//  ignored (color-rendering, color-interpolation, print-color-adjust,
//  forced-color-adjust, content-visibility, field-sizing, input-
//  security, interpolate-size, image-orientation, image-resolution).
//

import Foundation

struct RenderingConfig: Equatable {
    /// Raw string payload keyed by IR property type.
    var rawByType: [String: String] = [:]
    /// True when any owned property was seen.
    var touched: Bool = false
}
