//
//  AppearanceConfig.swift
//  StyleEngine/appearance — Phase 10.
//
//  appearance, appearance-variant, color-adjust, color-scheme,
//  image-rendering-quality. SwiftUI form controls don't expose
//  `appearance: none` — platform look is pinned.
//
//  NOTE: AccentColor is Phase 4 (color family) — not claimed here.
//

import Foundation

struct AppearanceConfig: Equatable {
    var rawByType: [String: String] = [:]
    var touched: Bool = false
}
