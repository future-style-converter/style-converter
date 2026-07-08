//
//  FilterExtractor.swift
//  StyleEngine/effects/filter — Phase 8.
//

import SwiftUI

enum FilterProperty {
    // Centralises owned names for PropertyRegistry + self-test.
    static let set: Set<String> = ["Filter", "BackdropFilter"]
}

enum FilterExtractor {

    static func extract(from properties: [IRProperty]) -> FilterConfig? {
        var cfg = FilterConfig()
        for p in properties {
            switch p.type {
            case "Filter":         cfg.filter   = parseList(p.data); cfg.touched = true
            case "BackdropFilter": cfg.backdrop = parseList(p.data); cfg.touched = true
            default: break
            }
        }
        return cfg.touched ? cfg : nil
    }

    // IR shapes:
    //   "none"                               → []
    //   { "url": "#id" }                     → [.url]
    //   [ { "fn": "blur", "r": {...} }, …]   → parsed functions
    private static func parseList(_ data: IRValue) -> [FilterFn] {
        if data.stringValue == "none" { return [] }
        if case .object(let o) = data, let url = o["url"]?.stringValue {
            return [.url(id: url)]
        }
        guard case .array(let arr) = data else { return [] }
        var out: [FilterFn] = []
        for entry in arr {
            guard case .object(let fn) = entry,
                  let name = fn["fn"]?.stringValue else { continue }
            if let parsed = parseFunction(name: name, fields: fn) {
                out.append(parsed)
            }
        }
        return out
    }

    // One CSS filter function → FilterFn. Known names are listed
    // explicitly; unknowns are dropped so an exotic fixture can't break
    // the whole chain.
    private static func parseFunction(name: String, fields: [String: IRValue]) -> FilterFn? {
        // Numeric shortcut.
        func numv(_ def: Double = 100) -> CGFloat {
            if let d = fields["v"]?.doubleValue { return CGFloat(d) }
            return CGFloat(def)
        }
        // `{ "px": N }` length.
        func px(_ k: String, _ def: Double = 0) -> CGFloat {
            if case .object(let o) = fields[k] ?? .null,
               let v = o["px"]?.doubleValue { return CGFloat(v) }
            return CGFloat(def)
        }
        // Angle in degrees.
        func degv(_ k: String, _ def: Double = 0) -> CGFloat {
            if let a = extractAngle(fields[k]) { return CGFloat(a.degrees) }
            return CGFloat(def)
        }
        switch name {
        case "blur":       return .blur(radius: px("r"))
        case "brightness": return .brightness(pct: numv())
        case "contrast":   return .contrast(pct: numv())
        case "grayscale":  return .grayscale(pct: numv())
        case "sepia":      return .sepia(pct: numv())
        case "invert":     return .invert(pct: numv())
        case "saturate":   return .saturate(pct: numv())
        case "opacity":    return .opacity(pct: numv())
        case "hue-rotate": return .hueRotate(deg: degv("a"))
        case "drop-shadow":
            // Drop-shadow carries x,y (lengths), optional r (blur) and
            // optional c (colour). Defaults: blur 0, colour nil (inherit).
            let colour = extractColor(fields["c"]).toSwiftUIColor()
            return .dropShadow(x: px("x"), y: px("y"),
                                blur: px("r"), color: colour)
        default:
            // Unknown function — drop silently.
            return nil
        }
    }
}
