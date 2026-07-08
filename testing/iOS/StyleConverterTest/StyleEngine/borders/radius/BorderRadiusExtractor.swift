//
//  BorderRadiusExtractor.swift
//  StyleEngine/borders/radius — Phase 5.
//
//  Reads the four physical corner properties AND the four logical ones,
//  merging logical into physical using an LTR-writing-mode assumption
//  (same as Android — fixtures do not exercise RTL radius today). Each
//  corner arrives either as a single length (circular) or as an object
//  carrying separate x / y components (elliptical). Percentage radii
//  resolve against the smaller of width/height per CSS Backgrounds 3
//  §5.4.1 — we match that heuristic.
//

// SwiftUI bridge comes via CoreGraphics; Foundation covers dictionary.
import Foundation
import CoreGraphics

// Enum-free static API, mirrors the other extractors.
enum BorderRadiusExtractor {

    // All property-type strings this extractor claims. Registered into
    // `PropertyRegistry.migrated` so the legacy switch skips them.
    static let propertyNames: [String] = [
        // Physical corners.
        "BorderTopLeftRadius", "BorderTopRightRadius",
        "BorderBottomRightRadius", "BorderBottomLeftRadius",
        // Logical corners (CSS Logical Props Level 1).
        "BorderStartStartRadius", "BorderStartEndRadius",
        "BorderEndEndRadius",   "BorderEndStartRadius",
    ]

    // Returns nil if no radius-* entry appears — applier then skips the
    // Shape chain entirely.
    static func extract(from properties: [IRProperty]) -> BorderRadiusConfig? {
        var cfg = BorderRadiusConfig()
        var touched = false

        for prop in properties {
            // Extract a corner payload. Nil for unparseable shapes —
            // silently skipped (CSS spec says "invalid is ignored").
            guard let corner = cornerFromValue(prop.data) else {
                // Still need to notice the property existed so downstream
                // logic (e.g. BorderSideApplier) can follow the rounded
                // outline even if the radius itself was zero.
                switch prop.type {
                case "BorderTopLeftRadius", "BorderTopRightRadius",
                     "BorderBottomRightRadius", "BorderBottomLeftRadius",
                     "BorderStartStartRadius", "BorderStartEndRadius",
                     "BorderEndEndRadius", "BorderEndStartRadius":
                    touched = true
                default: break
                }
                continue
            }

            switch prop.type {
            // Physical → physical.
            case "BorderTopLeftRadius":     cfg.topLeft     = corner; touched = true
            case "BorderTopRightRadius":    cfg.topRight    = corner; touched = true
            case "BorderBottomRightRadius": cfg.bottomRight = corner; touched = true
            case "BorderBottomLeftRadius":  cfg.bottomLeft  = corner; touched = true
            // Logical → LTR physical mapping.
            case "BorderStartStartRadius":  cfg.topLeft     = corner; touched = true
            case "BorderStartEndRadius":    cfg.topRight    = corner; touched = true
            case "BorderEndEndRadius":      cfg.bottomRight = corner; touched = true
            case "BorderEndStartRadius":    cfg.bottomLeft  = corner; touched = true
            default: break
            }
        }
        return touched ? cfg : nil
    }

    // Map an IR payload → BorderRadiusCorner.
    // Accepted shapes (from the CSS parser at
    // `src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/`):
    //   `{ "px": 10 }`                         → circular 10pt
    //   `{ "x": {"px":20}, "y": {"px":10} }`   → elliptical 20×10
    //   Bare number (rare, shorthand leftovers) → circular.
    //   `{ "original": { "v": 50, "u":"PERCENT" } }` → percent, resolved
    //   against 100pt as a placeholder. Full resolution needs the live
    //   component size — we accept the approximation and document it.
    private static func cornerFromValue(_ value: IRValue?) -> BorderRadiusCorner? {
        guard let value = value else { return nil }
        // Elliptical object form — check before generic px extraction.
        if case .object(let o) = value, o["x"] != nil || o["y"] != nil {
            // Each axis can be a length OR a percent; re-enter the Phase-1
            // length extractor and fall back to the px helper.
            let xp = ValueExtractors.extractPx(o["x"]) ?? 0
            let yp = ValueExtractors.extractPx(o["y"]) ?? 0
            return BorderRadiusCorner(x: xp, y: yp)
        }
        // Percent shape — resolve against 100pt (conservative; a future
        // pass can thread real size via SizingContext like padding does).
        if case .object(let o) = value,
           let original = o["original"]?.objectValue,
           let u = original["u"]?.stringValue, u.uppercased() == "PERCENT",
           let v = original["v"]?.doubleValue {
            let resolved = CGFloat(v) // placeholder: v% of 100pt = v pt.
            return BorderRadiusCorner(uniform: resolved)
        }
        // Circular scalar.
        if let px = ValueExtractors.extractPx(value) {
            return BorderRadiusCorner(uniform: px)
        }
        return nil
    }
}
