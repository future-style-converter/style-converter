//
//  BackgroundRepeatExtractor.swift
//  StyleEngine/background — Phase 4.
//
//  Parses BackgroundRepeat. Accepts both the simple string and the
//  per-axis object variant — see BackgroundRepeatConfig.swift for the
//  source shapes.
//

import Foundation

enum BackgroundRepeatProperty {
    static let names: [String] = ["BackgroundRepeat"]
}

enum BackgroundRepeatExtractor {

    static func extract(from properties: [IRProperty]) -> BackgroundRepeatConfig? {
        var layers: [BackgroundRepeatLayer] = []
        var seen = false
        for prop in properties where prop.type == "BackgroundRepeat" {
            seen = true
            layers = parseArray(prop.data)
        }
        guard seen, !layers.isEmpty else { return nil }
        return BackgroundRepeatConfig(layers: layers)
    }

    // Outer array → one per layer.
    private static func parseArray(_ v: IRValue) -> [BackgroundRepeatLayer] {
        guard case .array(let arr) = v else { return [] }
        return arr.map(parseLayer)
    }

    // Layer-level parse. Defaults to `repeat` when unknown — matches
    // CSS's default-when-omitted behaviour.
    private static func parseLayer(_ v: IRValue) -> BackgroundRepeatLayer {
        // Simple string form: same value on both axes.
        if case .string(let s) = v {
            let kw = s.lowercased()
            return BackgroundRepeatLayer(x: kw, y: kw)
        }
        // Object form: {x: ..., y: ...}.
        if case .object(let o) = v {
            let x = o["x"]?.stringValue?.lowercased() ?? "repeat"
            let y = o["y"]?.stringValue?.lowercased() ?? "repeat"
            return BackgroundRepeatLayer(x: x, y: y)
        }
        // Fallback — use CSS default.
        return BackgroundRepeatLayer(x: "repeat", y: "repeat")
    }
}
