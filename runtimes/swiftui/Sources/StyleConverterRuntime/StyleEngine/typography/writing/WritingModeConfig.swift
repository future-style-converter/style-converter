//
//  WritingModeConfig.swift
//  StyleEngine/typography/writing — Phase 6.
//
//  `writing-mode`: horizontal-tb | vertical-rl | vertical-lr | sideways-rl
//  | sideways-lr. We flag vertical / sideways modes in the aggregate so
//  TypographyApplier can emit a `.rotationEffect(.degrees(90))` fallback
//  — the best we can do without custom glyph layout.
//

import Foundation

struct WritingModeConfig: Equatable {
    /// True when the mode is any of the vertical / sideways variants.
    var isVertical: Bool = false
}
