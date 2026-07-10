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
    // `src/main/kotlin/app/parsing/css/properties/longhands/borders/radius/`
    // — verified against live converter output on fixtures/fidelity/
    // borders.combos.json):
    //   `{ "px": 10 }`                          → circular 10pt
    //   `{ "horizontal": {...}, "vertical": {...} }`
    //                                           → elliptical, each axis a
    //                                             length OR percent wrapper
    //   `{ "x": {"px":20}, "y": {"px":10} }`    → legacy elliptical form
    //   Bare number (rare, shorthand leftovers)  → circular.
    //   `{ "original": { "v": 50, "u":"PERCENT" } }` → percent of the
    //   border-box's corresponding dimension (CSS Backgrounds 3 §5.1).
    //   Percent axes are carried symbolically on the corner and resolved
    //   against the live rect in BorderRadiusShape.path(in:).
    private static func cornerFromValue(_ value: IRValue?) -> BorderRadiusCorner? {
        guard let value = value else { return nil }
        // Elliptical object form — the converter emits `horizontal` /
        // `vertical` for the two-value corner syntax ("40px 50%"); the
        // legacy `x` / `y` spelling is kept for compatibility. Checked
        // before generic px extraction so the pair never collapses.
        if case .object(let o) = value {
            let h = o["horizontal"] ?? o["x"]
            let v = o["vertical"]   ?? o["y"]
            if h != nil || v != nil {
                // Each axis independently: absolute px OR percent. A
                // percent axis rides through as `nil` px + a percent
                // component so "40px 50%" survives instead of degrading
                // to a square corner (the pre-fix behaviour dropped it).
                let (hx, hpct) = axisFromValue(h)
                let (vy, vpct) = axisFromValue(v)
                return BorderRadiusCorner(x: hx, y: vy,
                                          xPercent: hpct, yPercent: vpct)
            }
        }
        // Uniform percent shape — e.g. `border-radius: 50%`. Both axes
        // percent; resolution (x→width, y→height) happens at draw time
        // so a 200×80 box yields the web-matching 100×40 ellipse.
        if case .object(let o) = value,
           let original = o["original"]?.objectValue,
           let u = original["u"]?.stringValue, u.uppercased() == "PERCENT",
           let v = original["v"]?.doubleValue {
            return BorderRadiusCorner(x: 0, y: 0,
                                      xPercent: CGFloat(v), yPercent: CGFloat(v))
        }
        // Circular scalar.
        if let px = ValueExtractors.extractPx(value) {
            return BorderRadiusCorner(uniform: px)
        }
        return nil
    }

    // Decode one elliptical axis: `{px: N}` → (N, nil); percent wrapper
    // `{original:{v,u:PERCENT}}` → (0, v). Falls back to (0, nil) for
    // unresolvable payloads so the sibling axis still renders.
    private static func axisFromValue(_ v: IRValue?) -> (CGFloat, CGFloat?) {
        guard let v = v else { return (0, nil) }
        // Percent wrapper first — extractPx would return nil for it.
        if case .object(let o) = v,
           let original = o["original"]?.objectValue,
           original["u"]?.stringValue?.uppercased() == "PERCENT",
           let pv = original["v"]?.doubleValue {
            return (0, CGFloat(pv))
        }
        // Absolute length via the shared Phase-1 helper.
        return (ValueExtractors.extractPx(v) ?? 0, nil)
    }
}
