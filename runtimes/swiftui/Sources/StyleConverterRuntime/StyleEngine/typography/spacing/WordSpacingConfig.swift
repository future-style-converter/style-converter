//
//  WordSpacingConfig.swift
//  StyleEngine/typography/spacing — Phase 6.
//
//  `word-spacing`: adds extra space between words. SwiftUI Text has no
//  direct API (NSAttributedString needs `.kern` at word boundaries).
//  Config is preserved for future routing; applier is identity.
//

import CoreGraphics

struct WordSpacingConfig: Equatable { var px: CGFloat? = nil }
