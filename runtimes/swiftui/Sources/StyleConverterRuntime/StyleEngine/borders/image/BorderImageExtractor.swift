//
//  BorderImageExtractor.swift
//  StyleEngine/borders/image — Phase 5.
//
//  Pulls the five border-image properties out of the IR:
//    BorderImageSource / Slice / Width / Outset / Repeat.
//
//  IR shapes (see parser folder
//  `src/main/kotlin/app/parsing/css/properties/longhands/borders/image/`):
//    Source : {"type":"url","url":"..."} | {"type":"gradient","gradient":"..."}
//             | {"type":"none"}
//    Slice  : {"top": {"type":"number","value":N}, ..., "fill": true|false}
//    Width  : {"top": <dimension>, ..., "left": <dimension>}
//             dimension = {"type":"length","px":N} | {"type":"percentage","value":N}
//                       | {"type":"number","value":N} | {"type":"auto"}
//    Outset : same shape as width.
//    Repeat : "STRETCH" | {"horizontal":"REPEAT","vertical":"STRETCH"}
//
//  Mirrors the Android extractor byte-for-byte.
//

import Foundation
import CoreGraphics

enum BorderImageExtractor {

    // Register these in PropertyRegistry.migrated to skip the legacy switch.
    static let propertyNames: [String] = [
        "BorderImageSource", "BorderImageSlice", "BorderImageWidth",
        "BorderImageOutset", "BorderImageRepeat",
    ]

    // Returns nil when none of the five keys appear so the applier can
    // short-circuit.
    static func extract(from properties: [IRProperty]) -> BorderImageConfig? {
        var cfg = BorderImageConfig()
        var touched = false

        for prop in properties {
            switch prop.type {
            case "BorderImageSource":
                cfg.source = extractSource(prop.data); touched = true
            case "BorderImageSlice":
                extractSlice(prop.data, into: &cfg); touched = true
            case "BorderImageWidth":
                let t = extractFourValues(prop.data)
                cfg.widthTop = t.top; cfg.widthRight = t.right
                cfg.widthBottom = t.bottom; cfg.widthLeft = t.left
                touched = true
            case "BorderImageOutset":
                let t = extractFourValues(prop.data)
                cfg.outsetTop = t.top; cfg.outsetRight = t.right
                cfg.outsetBottom = t.bottom; cfg.outsetLeft = t.left
                touched = true
            case "BorderImageRepeat":
                let r = extractRepeat(prop.data)
                cfg.repeatHorizontal = r.h; cfg.repeatVertical = r.v
                touched = true
            default: break
            }
        }
        return touched ? cfg : nil
    }

    // MARK: - Source

    // Dispatch on the `type` tag. Unknown tags → .none so CSS's
    // "invalid values reset the property" rule holds.
    private static func extractSource(_ v: IRValue?) -> BorderImageSource {
        guard case .object(let o) = v else { return .none }
        let t = o["type"]?.stringValue?.lowercased() ?? ""
        switch t {
        case "url":      return .url(o["url"]?.stringValue ?? "")
        case "gradient": return .gradient(o["gradient"]?.stringValue ?? "")
        default:         return .none
        }
    }

    // MARK: - Slice

    // Populates the four slice edges + fill in-place. Separate function
    // so the main loop stays flat.
    private static func extractSlice(_ v: IRValue?,
                                     into cfg: inout BorderImageConfig) {
        guard case .object(let o) = v else { return }
        cfg.sliceTop    = extractSliceEdge(o["top"])
        cfg.sliceRight  = extractSliceEdge(o["right"])
        cfg.sliceBottom = extractSliceEdge(o["bottom"])
        cfg.sliceLeft   = extractSliceEdge(o["left"])
        // `fill` arrives either as a real boolean or as the string "true"
        // / "fill" depending on parser flavour — cover both paths.
        if let b = o["fill"]?.boolValue {
            cfg.sliceFill = b
        } else if let s = o["fill"]?.stringValue?.lowercased() {
            cfg.sliceFill = (s == "true" || s == "fill")
        }
    }

    // Edge shape: `{type: "number"|"percentage", value: N}`.
    private static func extractSliceEdge(_ v: IRValue?) -> BorderImageSliceEdge? {
        guard case .object(let o) = v,
              let t = o["type"]?.stringValue?.lowercased() else { return nil }
        // Try both `value` and `percentage` — different parser versions
        // name the scalar differently.
        let raw = (o["value"] ?? o["percentage"])?.doubleValue
        switch t {
        case "number":
            guard let n = raw else { return nil }
            return BorderImageSliceEdge(value: CGFloat(n), isPercent: false)
        case "percentage":
            guard let n = raw else { return nil }
            return BorderImageSliceEdge(value: CGFloat(n), isPercent: true)
        default:
            return nil
        }
    }

    // MARK: - Width / Outset (shared four-dimension shape)

    // Tuple-return to keep the caller readable.
    private struct FourDims {
        var top: BorderImageDimension?
        var right: BorderImageDimension?
        var bottom: BorderImageDimension?
        var left: BorderImageDimension?
    }

    private static func extractFourValues(_ v: IRValue?) -> FourDims {
        guard case .object(let o) = v else { return FourDims() }
        return FourDims(
            top:    extractDim(o["top"]),
            right:  extractDim(o["right"]),
            bottom: extractDim(o["bottom"]),
            left:   extractDim(o["left"])
        )
    }

    private static func extractDim(_ v: IRValue?) -> BorderImageDimension? {
        guard case .object(let o) = v,
              let t = o["type"]?.stringValue?.lowercased() else { return nil }
        switch t {
        case "auto":   return .auto
        case "length":
            // Reuse the Phase 1 px extractor — handles `{px: N}` + wrappers.
            guard let px = ValueExtractors.extractPx(.object(o)) else { return nil }
            return .length(px)
        case "percentage":
            let n = (o["value"] ?? o["percentage"])?.doubleValue ?? 0
            return .percent(CGFloat(n))
        case "number":
            guard let n = o["value"]?.doubleValue else { return nil }
            return .number(CGFloat(n))
        default:
            return nil
        }
    }

    // MARK: - Repeat

    // Two-axis result. Name-dodging the Swift `Repeat` builtin type.
    private struct RepeatAxes { var h: BorderImageRepeat; var v: BorderImageRepeat }

    private static func extractRepeat(_ v: IRValue?) -> RepeatAxes {
        // Single-string form applies to both axes.
        if case .string(let s) = v, let k = keyword(s) {
            return RepeatAxes(h: k, v: k)
        }
        // Object form separates horizontal/vertical.
        if case .object(let o) = v {
            let h = keyword(o["horizontal"]?.stringValue) ?? .stretch
            let vt = keyword(o["vertical"]?.stringValue) ?? h
            return RepeatAxes(h: h, v: vt)
        }
        return RepeatAxes(h: .stretch, v: .stretch)
    }

    // Upper-case keyword → enum. Unknowns collapse to .stretch (CSS default).
    private static func keyword(_ raw: String?) -> BorderImageRepeat? {
        guard let raw = raw?.uppercased() else { return nil }
        switch raw {
        case "STRETCH": return .stretch
        case "REPEAT":  return .repeatTile
        case "ROUND":   return .round
        case "SPACE":   return .space
        default:        return nil
        }
    }
}
