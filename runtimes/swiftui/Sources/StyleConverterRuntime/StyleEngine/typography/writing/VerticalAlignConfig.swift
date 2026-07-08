//
//  VerticalAlignConfig.swift
//  StyleEngine/typography/writing — Phase 6.
//
//  `vertical-align`: `baseline | sub | super | <length>` etc. SwiftUI has
//  `.baselineOffset(_:)` which accepts a signed length — so we resolve a
//  keyword to the nearest numeric offset and pass lengths through.
//

import CoreGraphics

struct VerticalAlignConfig: Equatable { var offsetPx: CGFloat? = nil }
