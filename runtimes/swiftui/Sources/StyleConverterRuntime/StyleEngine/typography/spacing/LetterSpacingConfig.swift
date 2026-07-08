//
//  LetterSpacingConfig.swift
//  StyleEngine/typography/spacing — Phase 6.
//
//  `letter-spacing` adds a per-glyph tracking offset. `normal` → no
//  override. A length (px/em→resolved-to-px) maps to SwiftUI's
//  `.tracking(_:)`.
//

import CoreGraphics

struct LetterSpacingConfig: Equatable { var px: CGFloat? = nil }
