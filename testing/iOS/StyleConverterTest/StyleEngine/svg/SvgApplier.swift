//
//  SvgApplier.swift
//  StyleEngine/svg — Phase 10.
//
//  Identity applier. SwiftUI's Shape/Path renderer is a separate
//  surface from the SDUI runtime (which emits View-based components),
//  so presentation properties like fill / stroke / paint-order only
//  render meaningfully inside a Shape node. Phase 10 keeps the Config
//  on hand for a future SVG-capable renderer to consume.
//
//  TODO(phase-svg): when an SVG sub-renderer lands, map:
//    • Fill.ColorValue                 → Shape.fill(Color(...))
//    • Stroke.ColorValue + StrokeWidth → Shape.stroke(Color, lineWidth:)
//    • StrokeDasharray                 → StrokeStyle(dash:)
//    • StrokeLinecap / StrokeLinejoin  → StrokeStyle.lineCap / .lineJoin
//    • StopColor / StopOpacity         → Gradient.Stop
//    • VectorEffect non-scaling-stroke → apply inside a Canvas with
//      transform-aware stroke-width.
//

import Foundation

enum SvgApplier {
    /// Identity. SwiftUI has no generic SVG-presentation apply path on
    /// View — only inside Shape/Path, which the SDUI runtime does not
    /// produce. Keeping the payload for audit.
    static func contribute(_ cfg: SvgConfig?) {
        _ = cfg
    }
}
