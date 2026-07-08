//
//  FontVariationSettingsConfig.swift
//  StyleEngine/typography/font — Phase 6.
//
//  `font-variation-settings` lists variable-font axis overrides as
//  `"tag" <number>` pairs. We keep the list; TypographyApplier is a
//  no-op until SwiftUI exposes a variable-font API.
//

import Foundation

struct FontVariationAxis: Equatable {
    var tag: String
    var value: Double
}

struct FontVariationSettingsConfig: Equatable {
    var axes: [FontVariationAxis] = []
}
