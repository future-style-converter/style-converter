//
//  MaskExtractor.swift
//  StyleEngine/effects/mask — Phase 8.
//

import SwiftUI

enum MaskProperty {
    // Owned by this extractor — unioned into PropertyRegistry.migrated.
    static let set: Set<String> = [
        "MaskImage", "MaskMode", "MaskRepeat",
        "MaskPosition", "MaskPositionX", "MaskPositionY",
        "MaskSize", "MaskOrigin", "MaskClip", "MaskComposite", "MaskType",
        "MaskBorderSource", "MaskBorderSlice", "MaskBorderWidth",
        "MaskBorderOutset", "MaskBorderRepeat", "MaskBorderMode",
    ]
}

enum MaskExtractor {

    static func extract(from properties: [IRProperty]) -> MaskConfig? {
        var cfg = MaskConfig()
        for p in properties {
            switch p.type {
            case "MaskImage":        parseImages(p.data, into: &cfg)
            case "MaskMode":         parseMode(p.data, into: &cfg)
            case "MaskRepeat":       parseRepeat(p.data, into: &cfg)
            case "MaskPosition":     parsePosition(p.data, into: &cfg)
            case "MaskPositionX":    parsePositionAxis(p.data, isY: false, into: &cfg)
            case "MaskPositionY":    parsePositionAxis(p.data, isY: true,  into: &cfg)
            case "MaskSize":         parseSize(p.data, into: &cfg)
            case "MaskOrigin":       cfg.origin = parseBox(p.data) ?? .borderBox; cfg.touched = true
            case "MaskClip":         cfg.clip   = parseBox(p.data) ?? .borderBox; cfg.touched = true
            case "MaskComposite":    parseComposite(p.data, into: &cfg)
            case "MaskType":         cfg.type = (p.data.stringValue == "ALPHA") ? .alpha : .luminance; cfg.touched = true
            case "MaskBorderSource": cfg.borderSource = p.data.stringValue; cfg.touched = true
            case "MaskBorderSlice":  cfg.borderSlice  = stringify(p.data); cfg.touched = true
            case "MaskBorderWidth":  cfg.borderWidth  = stringify(p.data); cfg.touched = true
            case "MaskBorderOutset": cfg.borderOutset = stringify(p.data); cfg.touched = true
            case "MaskBorderRepeat": cfg.borderRepeat = stringify(p.data); cfg.touched = true
            case "MaskBorderMode":   cfg.borderMode   = (p.data.stringValue == "ALPHA") ? .alpha : .luminance; cfg.touched = true
            default: break
            }
        }
        return cfg.touched ? cfg : nil
    }

    // MARK: - Mask image list

    // IR: an array (possibly of `"none"` strings, `"<url>"` strings, or
    // gradient objects).
    private static func parseImages(_ data: IRValue, into cfg: inout MaskConfig) {
        guard case .array(let arr) = data else { return }
        var layers: [MaskLayer] = []
        for entry in arr {
            if let s = entry.stringValue {
                if s == "none" { layers.append(.none) }
                else { layers.append(.url(href: s)) }
                continue
            }
            guard case .object(let o) = entry,
                  let t = o["type"]?.stringValue else { continue }
            switch t {
            case "linear-gradient":
                // Optional angle; default 180° (top→bottom) per CSS spec.
                let angle = extractAngle(o["angle"])?.degrees ?? 180
                layers.append(.linearGradient(angleDeg: angle,
                                              stops: parseStops(o["stops"])))
            case "radial-gradient", "repeating-radial-gradient":
                layers.append(.radialGradient(stops: parseStops(o["stops"])))
            case "repeating-linear-gradient":
                let angle = extractAngle(o["angle"])?.degrees ?? 180
                layers.append(.linearGradient(angleDeg: angle,
                                              stops: parseStops(o["stops"])))
            case "conic-gradient":
                layers.append(.conicGradient(stops: parseStops(o["stops"])))
            default: break
            }
        }
        cfg.images = layers
        cfg.touched = true
    }

    // Gradient stops: `[{ "color": {srgb}, "position": N? }]`.
    private static func parseStops(_ v: IRValue?) -> [Gradient.Stop] {
        guard let arr = v?.arrayValue else { return [] }
        // Even if `position` is nil we pass through — SwiftUI infers
        // evenly-spaced stops when we use `Gradient(colors:)` so we
        // emit both color-and-location variants below.
        var stops: [Gradient.Stop] = []
        for (i, entry) in arr.enumerated() {
            guard case .object(let o) = entry else { continue }
            let color = extractColor(o["color"]).toSwiftUIColor() ?? .clear
            let pos: CGFloat
            if let p = o["position"]?.doubleValue {
                pos = CGFloat(p / 100)   // CSS stops are percentages.
            } else {
                // Distribute evenly along [0,1] so the gradient paints
                // even without explicit positions.
                pos = arr.count > 1
                    ? CGFloat(i) / CGFloat(arr.count - 1)
                    : 0
            }
            stops.append(.init(color: color, location: pos))
        }
        return stops
    }

    // MARK: - Enum keyword fields

    // MaskMode arrives as `{ "type": "app.…Alpha|Luminance|MatchSource" }`.
    private static func parseMode(_ data: IRValue, into cfg: inout MaskConfig) {
        guard case .object(let o) = data, let t = o["type"]?.stringValue else { return }
        if t.hasSuffix("Alpha")       { cfg.mode = .alpha }
        else if t.hasSuffix("Luminance") { cfg.mode = .luminance }
        else                           { cfg.mode = .matchSource }
        cfg.touched = true
    }

    // Similar long-type-name discrimination.
    private static func parseRepeat(_ data: IRValue, into cfg: inout MaskConfig) {
        guard case .object(let o) = data, let t = o["type"]?.stringValue else { return }
        if t.hasSuffix("NoRepeat") { cfg.repeatMode = .noRepeat }
        else if t.hasSuffix("RepeatX") { cfg.repeatMode = .repeatX }
        else if t.hasSuffix("RepeatY") { cfg.repeatMode = .repeatY }
        else if t.hasSuffix("Round")   { cfg.repeatMode = .round }
        else if t.hasSuffix("Space")   { cfg.repeatMode = .space }
        else                           { cfg.repeatMode = .repeatBoth }
        cfg.touched = true
    }

    private static func parseComposite(_ data: IRValue, into cfg: inout MaskConfig) {
        guard case .object(let o) = data, let t = o["type"]?.stringValue else { return }
        if t.hasSuffix("Subtract")      { cfg.composite = .subtract }
        else if t.hasSuffix("Intersect") { cfg.composite = .intersect }
        else if t.hasSuffix("Exclude")   { cfg.composite = .exclude }
        else                             { cfg.composite = .add }
        cfg.touched = true
    }

    // MARK: - Position + size

    // Position arrives as an array (one entry per layer). We only honour
    // the first layer — SwiftUI can't position per-layer masks anyway.
    private static func parsePosition(_ data: IRValue, into cfg: inout MaskConfig) {
        guard case .array(let arr) = data, let first = arr.first,
              case .object(let o) = first else { return }
        var pos = MaskPositionValue()
        if let x = axisFraction(o["x"]) { pos.x = CGFloat(x) }
        if let y = axisFraction(o["y"]) { pos.y = CGFloat(y) }
        cfg.position = pos
        cfg.touched = true
    }

    // Axis-specific longhand for `mask-position-x` / `-y`.
    private static func parsePositionAxis(_ data: IRValue, isY: Bool,
                                          into cfg: inout MaskConfig) {
        // The axis longhand ships the axis blob directly (no array wrapper).
        guard let v = axisFraction(data) else { return }
        if isY { cfg.position.y = CGFloat(v) }
        else   { cfg.position.x = CGFloat(v) }
        cfg.touched = true
    }

    // Shared axis → fraction helper. Supports keyword / percentage /
    // length-fallback (length maps to 0.5 TODO — needs view bounds).
    private static func axisFraction(_ v: IRValue?) -> Double? {
        guard let v = v, case .object(let o) = v else { return nil }
        if let kw = o["keyword"]?.stringValue ?? o["value"]?.stringValue {
            switch kw.uppercased() {
            case "LEFT", "TOP":    return 0.0
            case "CENTER":         return 0.5
            case "RIGHT", "BOTTOM": return 1.0
            default: break
            }
        }
        if let p = o["percentage"]?.doubleValue { return p / 100 }
        if let orig = o["original"]?.objectValue,
           let vv = orig["v"]?.doubleValue,
           orig["u"]?.stringValue == "PERCENT" { return vv / 100 }
        // A raw length without bounds info — best-effort neutral.
        return 0.5
    }

    // MaskSize — four shapes: auto / cover / contain / explicit pair.
    private static func parseSize(_ data: IRValue, into cfg: inout MaskConfig) {
        guard case .object(let o) = data else { return }
        if let kw = o["keyword"]?.stringValue {
            switch kw.uppercased() {
            case "AUTO":    cfg.size = .auto
            case "COVER":   cfg.size = .cover
            case "CONTAIN": cfg.size = .contain
            default: break
            }
            cfg.touched = true
            return
        }
        // Explicit widths — IR uses `width` and `height` sub-blobs.
        let (w, wp) = axisLength(o["width"])
        let (h, hp) = axisLength(o["height"])
        cfg.size = .explicit(width: w, height: h,
                             widthPercent: wp, heightPercent: hp)
        cfg.touched = true
    }

    // Returns (pointsOpt, percentOpt). Accepts `{ "keyword":"auto"}` too.
    private static func axisLength(_ v: IRValue?) -> (CGFloat?, CGFloat?) {
        guard let v = v, case .object(let o) = v else { return (nil, nil) }
        if o["keyword"]?.stringValue?.uppercased() == "AUTO" { return (nil, nil) }
        if let px = o["px"]?.doubleValue { return (CGFloat(px), nil) }
        if let orig = o["original"]?.objectValue,
           let vv = orig["v"]?.doubleValue,
           orig["u"]?.stringValue == "PERCENT" { return (nil, CGFloat(vv / 100)) }
        return (nil, nil)
    }

    // Box-reference keyword mapping shared by origin/clip.
    private static func parseBox(_ data: IRValue) -> MaskBoxRef? {
        switch data.stringValue {
        case "CONTENT_BOX": return .contentBox
        case "PADDING_BOX": return .paddingBox
        case "BORDER_BOX":  return .borderBox
        case "FILL_BOX":    return .fillBox
        case "STROKE_BOX":  return .strokeBox
        case "VIEW_BOX":    return .viewBox
        case "NO_CLIP":     return .noClip
        default:            return nil
        }
    }

    // Cheap stringify for border-* storage — we don't render them.
    private static func stringify(_ v: IRValue) -> String {
        switch v {
        case .string(let s): return s
        case .double(let d): return String(d)
        case .int(let i):    return String(i)
        case .bool(let b):   return String(b)
        default: return "<complex>"
        }
    }
}
