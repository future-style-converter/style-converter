//
//  TextUnderlineOffsetConfig.swift
//  StyleEngine/typography/decoration — Phase 6.
//
//  Gap between baseline and the underline stroke. SwiftUI has no API; the
//  value is captured for audit / future attributed-string rendering.
//

import CoreGraphics

struct TextUnderlineOffsetConfig: Equatable { var px: CGFloat? = nil }
