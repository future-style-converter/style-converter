//
//  TextDecorationThicknessConfig.swift
//  StyleEngine/typography/decoration — Phase 6.
//
//  `text-decoration-thickness`: length. SwiftUI has no native thickness
//  control for the underline/strikethrough stroke; value captured for audit.
//

import CoreGraphics

struct TextDecorationThicknessConfig: Equatable { var px: CGFloat? = nil }
