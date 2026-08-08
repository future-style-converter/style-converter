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

    /// Wave 35 (lane B5) — the keyword itself, not just "is it vertical".
    ///
    /// `isVertical` cannot tell `vertical-rl` from `vertical-lr`, and the
    /// side successive LINES stack on is exactly that difference
    /// (css-writing-modes-4 §3). `VerticalTextFlow.lineStack` consumes this.
    /// Defaults to `.horizontalTb` so every existing reader — all of which
    /// only ask `isVertical` — is byte-identical; the Compose twin's
    /// `WritingModeConfig.writingMode` is the same field.
    var mode: WritingModeValue = .horizontalTb
}
