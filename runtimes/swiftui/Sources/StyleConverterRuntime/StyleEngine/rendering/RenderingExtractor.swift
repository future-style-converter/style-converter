//
//  RenderingExtractor.swift
//  StyleEngine/rendering — Phase 10.
//

import Foundation

/// Registry ownership for the rendering-hint family. Names mirror the
/// IR-model stems under `app/irmodels/properties/rendering/`.
enum RenderingProperty {
    static let names: [String] = [
        "ColorInterpolation", "ColorInterpolationFilters", "ColorRendering",
        "ContentVisibility", "FieldSizing",
        "ForcedColorAdjust", "PrintColorAdjust",
        "ImageOrientation", "ImageResolution",
        "InputSecurity", "InterpolateSize", "Zoom",
    ]
    static var set: Set<String> { Set(names) }
}

enum RenderingExtractor {
    static func extract(from properties: [IRProperty]) -> RenderingConfig? {
        var cfg = RenderingConfig()
        let owned = RenderingProperty.set
        for p in properties where owned.contains(p.type) {
            cfg.touched = true
            if let kw = ValueExtractors.extractKeyword(p.data) {
                cfg.rawByType[p.type] = kw
            } else {
                cfg.rawByType[p.type] = String(describing: p.data)
            }
        }
        return cfg.touched ? cfg : nil
    }
}
