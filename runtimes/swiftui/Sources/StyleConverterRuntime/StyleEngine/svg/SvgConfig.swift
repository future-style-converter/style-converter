//
//  SvgConfig.swift
//  StyleEngine/svg — Phase 10.
//
//  Holds the parsed payload for the SVG-presentation family — fill,
//  stroke (+ all its sub-properties), paint-order, shape-rendering,
//  marker-*, stop-*, flood-*, lighting-color, vector-effect,
//  buffered-rendering, enable-background, and the geometry shorthands
//  cx/cy/r/rx/ry/x/y/d. 34 IR property type names — see
//  `SvgProperty.names`.
//
//  These apply only inside SwiftUI Shape/Path rendering, which the
//  SDUI runtime does not produce today — Compose/SwiftUI components
//  are View-based. We preserve the IR values in `rawByType` so a
//  future SVG-capable renderer can pick them up; there is no applier at
//  all (retro P2b deleted the identity one, A6#10). The mapping that
//  renderer will need, kept here rather than lost with it:
//    • Fill.ColorValue                 → Shape.fill(Color(...))
//    • Stroke.ColorValue + StrokeWidth → Shape.stroke(Color, lineWidth:)
//    • StrokeDasharray                 → StrokeStyle(dash:)
//    • StrokeLinecap / StrokeLinejoin  → StrokeStyle.lineCap / .lineJoin
//    • StopColor / StopOpacity         → Gradient.Stop
//    • VectorEffect non-scaling-stroke → stroke inside a Canvas with a
//      transform-aware line width (SVG 2, the `vector-effect` property).
//

import Foundation

struct SvgConfig: Equatable {
    /// Raw string payload keyed by IR property type. Fill-paint values
    /// with structured `{type, ...}` shapes fall through to a debug
    /// dump; simple keywords land as plain strings.
    var rawByType: [String: String] = [:]
    /// True when the extractor saw at least one owned property.
    var touched: Bool = false
}
