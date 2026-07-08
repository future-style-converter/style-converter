//
//  SvgExtractor.swift
//  StyleEngine/svg — Phase 10.
//
//  Walks the property list once and captures every SVG-presentation
//  family property into SvgConfig. Each claimed name is registered
//  via `SvgProperty.set` unioned into `PropertyRegistry.migrated`.
//

import Foundation

/// Registry ownership for the SVG family. Mirrors every file under
/// `src/main/kotlin/app/irmodels/properties/svg/` with the `Property`
/// suffix stripped (the IR serialises the class stem).
enum SvgProperty {
    /// Explicit name list — 34 entries, matching the README-phase10
    /// fixture's 87-component coverage (variants × property = 87,
    /// properties = 34).
    static let names: [String] = [
        // Paint — fill family.
        "Fill", "FillRule", "FillOpacity",
        // Paint — stroke family.
        "Stroke", "StrokeWidth", "StrokeOpacity",
        "StrokeDasharray", "StrokeDashoffset",
        "StrokeLinecap", "StrokeLinejoin", "StrokeMiterlimit",
        // Gradient / filter endpoints.
        "StopColor", "StopOpacity",
        "FloodColor", "FloodOpacity",
        "LightingColor",
        // Render-hint family.
        "ColorInterpolation", "ColorInterpolationFilters", "ColorRendering",
        "ShapeRendering", "VectorEffect", "BufferedRendering", "EnableBackground",
        "PaintOrder",
        // Markers.
        "Marker", "MarkerStart", "MarkerMid", "MarkerEnd", "MarkerSide",
        // Geometry shorthands — cx/cy/r/rx/ry/x/y/d.
        "Cx", "Cy", "R", "Rx", "Ry", "X", "Y", "D",
    ]
    /// Set form for the PropertyRegistry union.
    static var set: Set<String> { Set(names) }
}

enum SvgExtractor {

    /// Single pass over the property list; non-owned names are skipped.
    static func extract(from properties: [IRProperty]) -> SvgConfig? {
        var cfg = SvgConfig()
        let owned = SvgProperty.set
        for p in properties where owned.contains(p.type) {
            // Fill/Stroke carry structured payloads ({type, color?, url?,
            // fallback?}) — we debug-describe to preserve fidelity.
            // Simple keyword values (FillRule, StrokeLinecap, etc.)
            // normalise through extractKeyword.
            if let kw = ValueExtractors.extractKeyword(p.data) {
                cfg.rawByType[p.type] = kw
            } else {
                cfg.rawByType[p.type] = String(describing: p.data)
            }
            cfg.touched = true
        }
        return cfg.touched ? cfg : nil
    }
}
