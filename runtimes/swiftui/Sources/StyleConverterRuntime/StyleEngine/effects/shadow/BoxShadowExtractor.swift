//
//  BoxShadowExtractor.swift
//  StyleEngine/effects/shadow — Phase 5.
//
//  The IR delivers box-shadow as an array of shadow objects:
//    [{
//       "x": {"px": N}, "y": {"px": N},
//       "blur": {"px": N}, "spread": {"px": N}, // optional
//       "c": {"srgb": { r, g, b, a? }, ...},    // optional — "c" key
//       "inset": true|false                     // optional
//    }, ...]
//
//  Occasionally the converter emits a plain string when it gives up
//  (e.g. the shadow carried a calc() the parser didn't expand) — we log
//  that as "unknown" by returning an empty layer list.
//

import Foundation
import CoreGraphics

enum BoxShadowExtractor {

    // Single property in this extractor's surface — listed here so the
    // PropertyRegistry.migrated set picks it up.
    static let propertyNames: [String] = ["BoxShadow"]

    // Returns nil when the IR has no BoxShadow property or when it's an
    // empty array. Non-empty garbage (string fallbacks) returns nil as
    // well so the applier leaves the element untouched.
    static func extract(from properties: [IRProperty]) -> BoxShadowConfig? {
        for prop in properties where prop.type == "BoxShadow" {
            return parse(prop.data)
        }
        return nil
    }

    // Accept both the canonical `[...]` array form and the occasional
    // `{ shadows: [...] }` wrapper (seen in early StyleBuilder tests).
    private static func parse(_ v: IRValue) -> BoxShadowConfig? {
        let arr: [IRValue]
        switch v {
        case .array(let a): arr = a
        case .object(let o):
            // Wrapper form. When `shadows` is absent, treat the whole
            // object as a single shadow entry.
            if case .array(let a) = o["shadows"] ?? .null { arr = a }
            else { arr = [.object(o)] }
        // String fallback (calc expression still glued together) — no
        // layers we can render honestly.
        case .string: return nil
        default: return nil
        }
        // Empty array = property present but effectively `none`.
        if arr.isEmpty { return nil }

        // Map each entry to a BoxShadowLayer; skip malformed entries
        // silently (CSS's "invalid is ignored" rule).
        let layers: [BoxShadowLayer] = arr.compactMap(parseLayer)
        return layers.isEmpty ? nil : BoxShadowConfig(layers: layers)
    }

    private static func parseLayer(_ v: IRValue) -> BoxShadowLayer? {
        guard case .object(let o) = v else { return nil }
        var layer = BoxShadowLayer()
        // Offsets — `x|offsetX`, `y|offsetY`. Both mandatory per spec but
        // we tolerate missing fields by defaulting to 0.
        layer.x      = ValueExtractors.extractPx(o["x"] ?? o["offsetX"]) ?? 0
        layer.y      = ValueExtractors.extractPx(o["y"] ?? o["offsetY"]) ?? 0
        layer.blur   = ValueExtractors.extractPx(o["blur"] ?? o["blurRadius"]) ?? 0
        layer.spread = ValueExtractors.extractPx(o["spread"] ?? o["spreadRadius"]) ?? 0
        // Colour key is `c` in the canonical IR (short form). Accept the
        // long `color` spelling too for forward compat.
        layer.color  = ValueExtractors.extractColor(o["c"] ?? o["color"])
        layer.inset  = o["inset"]?.boolValue ?? false
        return layer
    }
}
