//
//  BackgroundSizeExtractor.swift
//  StyleEngine/background — Phase 4.
//
//  Parses the `BackgroundSize` IR array. See BackgroundSizeConfig.swift
//  for the shape documentation.
//

import Foundation

enum BackgroundSizeProperty {
    static let names: [String] = ["BackgroundSize"]
}

enum BackgroundSizeExtractor {

    // Single-pass; last-wins. Returns nil when no BackgroundSize property.
    static func extract(from properties: [IRProperty]) -> BackgroundSizeConfig? {
        var layers: [BackgroundSizeLayer] = []
        var seen = false
        for prop in properties where prop.type == "BackgroundSize" {
            seen = true
            layers = parseArray(prop.data)
        }
        guard seen, !layers.isEmpty else { return nil }
        return BackgroundSizeConfig(layers: layers)
    }

    // Parse the outer array. The IR always wraps sizes in an array so
    // per-layer access is uniform.
    private static func parseArray(_ v: IRValue) -> [BackgroundSizeLayer] {
        guard case .array(let arr) = v else { return [] }
        return arr.map(parseLayer)
    }

    // Parse a single layer entry. Unrecognised shapes degrade to `.auto`
    // so rendering continues.
    private static func parseLayer(_ v: IRValue) -> BackgroundSizeLayer {
        // Bare-string keyword path.
        if case .string(let s) = v {
            switch s.lowercased() {
            case "cover":   return .cover
            case "contain": return .contain
            case "auto":    return .auto
            default:        return .auto
            }
        }
        // Object path: `{w: ..., h: ...?}`.
        if case .object(let o) = v {
            let w = parseDim(o["w"])
            // When `h` is absent the CSS spec means "auto" — let the
            // applier keep the aspect ratio intact.
            let h = o["h"].map(parseDim) ?? .auto
            return .explicit(w: w, h: h)
        }
        // Anything else — unrecognised shape, fall back to auto.
        return .auto
    }

    // Parse one dimension. Two valid shapes:
    //   {px: N}     → absolute
    //   bare Double → percent (per the brief's finding)
    //   bare Int    → percent, too (IR emits ints occasionally)
    private static func parseDim(_ v: IRValue?) -> BackgroundSizeDim {
        guard let v = v else { return .auto }
        if case .object(let o) = v, let px = o["px"]?.doubleValue {
            return .px(px)
        }
        if case .double(let d) = v { return .percent(d) }
        if case .int(let i) = v    { return .percent(Double(i)) }
        // String keyword `"auto"` occasionally slips in at the dim level.
        if case .string(let s) = v, s.lowercased() == "auto" { return .auto }
        return .auto
    }
}
