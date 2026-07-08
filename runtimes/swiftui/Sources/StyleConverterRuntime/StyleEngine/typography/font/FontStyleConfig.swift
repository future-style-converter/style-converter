//
//  FontStyleConfig.swift
//  StyleEngine/typography/font — Phase 6.
//
//  CSS `font-style`: `normal | italic | oblique [ <angle> ]`. SwiftUI's
//  `Font.italic()` is boolean — we collapse `italic` and `oblique` to
//  true and drop the optional oblique angle (TODO for a custom renderer).
//

import Foundation

/// Italic flag. `nil` → inherit; `false` → explicit `normal`.
struct FontStyleConfig: Equatable {
    /// True when italic or oblique was declared.
    var italic: Bool? = nil
}
