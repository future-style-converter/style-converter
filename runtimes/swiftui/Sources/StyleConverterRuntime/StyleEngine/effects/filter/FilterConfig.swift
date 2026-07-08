//
//  FilterConfig.swift
//  StyleEngine/effects/filter — Phase 8.
//
//  CSS `filter` + `backdrop-filter` share the same function palette:
//  blur / brightness / contrast / grayscale / sepia / invert /
//  saturate / hue-rotate / opacity / drop-shadow / url(#id).
//
//  Ordering matters — blur-then-brightness is visually different from
//  brightness-then-blur. We preserve the declared order so the applier
//  can fold modifiers one at a time in the same sequence.
//

import SwiftUI

// A single filter function.
enum FilterFn: Equatable {
    // Length-based blur radius in points.
    case blur(radius: CGFloat)
    // Percentage-based tone adjustments. Values are in CSS form:
    // 100 ≡ identity, 0 ≡ fully off, >100 amplifies. We keep them in
    // CSS's 0–N range (not divided by 100) so the applier can map
    // precisely.
    case brightness(pct: CGFloat)
    case contrast(pct: CGFloat)
    case grayscale(pct: CGFloat)
    case sepia(pct: CGFloat)
    case invert(pct: CGFloat)
    case saturate(pct: CGFloat)
    case opacity(pct: CGFloat)
    // Angle-based hue rotation, in degrees.
    case hueRotate(deg: CGFloat)
    // Drop shadow — x, y offset, blur radius, colour.
    case dropShadow(x: CGFloat, y: CGFloat, blur: CGFloat, color: Color?)
    // `url(#id)` — no SwiftUI analog; surface the id for logging.
    case url(id: String)
}

struct FilterConfig: Equatable {
    // Foreground filter chain (`filter:` property).
    var filter: [FilterFn] = []
    // Backdrop filter chain (`backdrop-filter:` property).
    var backdrop: [FilterFn] = []
    // Touched flag — set once the extractor populates either list.
    var touched: Bool = false
}
