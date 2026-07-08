//
//  FontStretchConfig.swift
//  StyleEngine/typography/font — Phase 6.
//
//  `font-stretch` maps 9 keywords + `<percentage>` to a width percentage
//  where 100% = normal. SwiftUI has no direct API — we record the value
//  but the applier is a no-op (TODO). See FontStretchPropertyParser.kt.
//

import CoreGraphics

/// Percentage width, 50% (ultra-condensed) … 200% (ultra-expanded).
struct FontStretchConfig: Equatable { var percent: CGFloat? = nil }
