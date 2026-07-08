//
//  BackgroundImageExtractor.swift
//  StyleEngine/background — Phase 4.
//
//  Parses the `BackgroundImage` IR into an ordered layer list. The IR is
//  always an array (even for a single layer). Each element is one of:
//    - Bare string "none"
//    - Object { url: String, data: Bool } — URL or data URI
//    - Object { type: "linear-gradient"|"radial-gradient"|"conic-gradient"
//              |"repeating-linear-gradient"|... , angle?: {deg}, stops: [...] }
//  Quirks:
//    * Radial shape-keywords (circle/ellipse/closest-side) appear as the
//      FIRST entry inside `stops` with no srgb — brief says detect and
//      skip these; we also capture the keyword for shape hinting.
//    * Stop position is 0..100 percentage (may be null); we normalise to
//      0..1 Double here so the applier doesn't need to remember.
//

import Foundation

enum BackgroundImageProperty {
    static let names: [String] = ["BackgroundImage"]
}

enum BackgroundImageExtractor {

    // Entry point. Returns nil when no `BackgroundImage` property exists
    // or the array was empty / all-none.
    static func extract(from properties: [IRProperty]) -> BackgroundImageConfig? {
        // Last-wins cascade — capture the final BackgroundImage seen.
        var layers: [BackgroundImageLayer] = []
        var seen = false
        for prop in properties where prop.type == "BackgroundImage" {
            seen = true
            layers = parseLayerArray(prop.data)
        }
        // Return nil in both "absent" and "[]" cases — neither needs the
        // applier to do anything.
        guard seen, !layers.isEmpty else { return nil }
        return BackgroundImageConfig(layers: layers)
    }

    // Parse the outer array. Defensive: wrap a single-object IR in an
    // array just in case an older IR shape slips in.
    private static func parseLayerArray(_ v: IRValue) -> [BackgroundImageLayer] {
        // Normal case — outer array.
        if case .array(let arr) = v {
            return arr.compactMap(parseLayer)
        }
        // Fallback single-layer path.
        if let one = parseLayer(v) { return [one] }
        return []
    }

    // Decode one layer entry. Returns nil to drop unrecognised entries
    // rather than render a blank "none" in their place.
    private static func parseLayer(_ v: IRValue) -> BackgroundImageLayer? {
        // `none` literal — bare CSS keyword, IR keeps it as a string.
        if case .string(let s) = v, s.lowercased() == "none" {
            return BackgroundImageLayer.none
        }
        // Must be an object for any structured layer.
        guard case .object(let o) = v else { return nil }

        // URL layer: `{url: "...", data: Bool}`.
        if let url = o["url"]?.stringValue {
            return .url(url)
        }

        // Gradient layer — switch on the `type` discriminator.
        guard let t = o["type"]?.stringValue?.lowercased() else { return nil }
        let angle = readAngle(o["angle"])
        let rawStops = o["stops"]?.arrayValue ?? []
        // Parse stops via the shared helper; it also yields any shape
        // keyword captured from a malformed first entry.
        let parsed = parseStops(rawStops)
        // CSS `at <position>` for radial / conic gradients lands in the
        // IR under `pos: {x: <pct>, y: <pct>}` — mirror what the Android
        // extractor reads so the same audit fixtures (e.g.
        // Audit_ConicFrom30degAt25, Audit_RadialEllipseClosestSide with
        // off-centre position) honour the centre instead of always
        // rendering at 50% / 50%.
        let pos = o["pos"]?.objectValue
        let cx = (pos?["x"]?.doubleValue).map { $0 / 100.0 } ?? 0.5
        let cy = (pos?["y"]?.doubleValue).map { $0 / 100.0 } ?? 0.5

        switch t {
        case "linear-gradient":
            return .linear(angleDeg: angle, stops: parsed.stops)
        case "radial-gradient":
            return .radial(shape: parsed.shapeKeyword, stops: parsed.stops, cx: cx, cy: cy)
        case "conic-gradient":
            return .conic(fromDeg: angle, stops: parsed.stops, cx: cx, cy: cy)
        case "repeating-linear-gradient":
            return .repeating(kind: .linear, angleDeg: angle, stops: parsed.stops)
        case "repeating-radial-gradient":
            return .repeating(kind: .radial, angleDeg: nil, stops: parsed.stops)
        case "repeating-conic-gradient":
            return .repeating(kind: .conic, angleDeg: angle, stops: parsed.stops)
        default:
            return nil
        }
    }

    // Angle helper. IR shape: `{deg: Double}`.
    private static func readAngle(_ v: IRValue?) -> Double? {
        guard case .object(let o) = v else { return nil }
        return o["deg"]?.doubleValue
    }

    // Stop parser with shape-keyword recovery for radial gradients.
    // Any stop whose `color` object has no `srgb` AND is not a dynamic
    // colour is treated as a shape keyword leakage; we capture the
    // `original` string and skip that stop.
    private static func parseStops(_ raw: [IRValue])
        -> (stops: [BackgroundImageStop], shapeKeyword: String?) {

        var out: [BackgroundImageStop] = []
        var shape: String? = nil

        for entry in raw {
            guard case .object(let o) = entry else { continue }
            // Each stop is {color: IRValue, position: Double?|null}.
            let colorValue = o["color"] ?? .null
            let positionRaw = o["position"]

            // Detect the malformed "shape-as-stop" entry: color has only
            // `original` (a string keyword) and no `srgb` / no `type`.
            if case .object(let co) = colorValue,
               co["srgb"] == nil,
               co["original"]?.stringValue != nil,
               co["type"] == nil {
                if shape == nil {
                    shape = co["original"]?.stringValue
                }
                continue
            }

            // Normal stop: colour + optional position.
            let color = extractColor(colorValue)
            let pos: Double? = {
                if let d = positionRaw?.doubleValue { return d / 100.0 }
                return nil
            }()
            out.append(BackgroundImageStop(color: color, position: pos))
        }
        return (out, shape)
    }
}
