//
//  FontFeatureSettingsConfig.swift
//  StyleEngine/typography/font — Phase 6.
//
//  `font-feature-settings` is a list of `"tag" <number|on|off>` pairs
//  mapped to OpenType features. SwiftUI Text has no direct API; we hold
//  the list for future NSAttributedString interop.
//

import Foundation

/// One feature = 4-char OpenType tag + integer value.
struct FontFeatureTag: Equatable {
    var tag: String
    var value: Int
}

struct FontFeatureSettingsConfig: Equatable {
    /// Declared features in order; empty when property was `normal`.
    var features: [FontFeatureTag] = []
}
