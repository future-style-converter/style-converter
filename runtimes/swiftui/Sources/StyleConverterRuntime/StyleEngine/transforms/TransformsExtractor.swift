//
//  TransformsExtractor.swift
//  StyleEngine/transforms — Phase 8.
//
//  Parses every transforms-family property out of an IRProperty list and
//  folds the result into a single `TransformsAggregate`. Each property
//  has its own helper so the file stays navigable; the top-level
//  `extract` returns `nil` when nothing was written.
//

// SwiftUI for UnitPoint used in origin parsing.
import SwiftUI

// Property-type registry — used by PropertyRegistry.migrated and
// TransformsSelfTest so every owned name lives in exactly one place.
enum TransformsProperty {
    static let set: Set<String> = [
        "Transform", "Rotate", "Scale", "Translate",
        "TransformOrigin", "TransformBox", "TransformStyle",
        "Perspective", "PerspectiveOrigin", "BackfaceVisibility",
    ]
}

enum TransformsExtractor {

    /// Top-level. Returns nil when no property in the list matches the
    /// transforms family. Matches the spacing/borders/typography pattern.
    static func extract(from properties: [IRProperty]) -> TransformsAggregate? {
        // Mutable aggregate; each `apply*` helper may set `touched`.
        var agg = TransformsAggregate()
        // Loop once — registry membership per-entry is cheap.
        for p in properties {
            switch p.type {
            case "Transform":          applyTransform(p.data, into: &agg)
            case "Rotate":             applyRotate(p.data, into: &agg)
            case "Scale":              applyScale(p.data, into: &agg)
            case "Translate":          applyTranslate(p.data, into: &agg)
            case "TransformOrigin":    applyOrigin(p.data, into: &agg)
            case "TransformBox":       applyBox(p.data, into: &agg)
            case "TransformStyle":     applyStyle(p.data, into: &agg)
            case "Perspective":        applyPerspective(p.data, into: &agg)
            case "PerspectiveOrigin":  applyPerspectiveOrigin(p.data, into: &agg)
            case "BackfaceVisibility": applyBackface(p.data, into: &agg)
            default: break
            }
        }
        // Short-circuit when nothing was written.
        return agg.touched ? agg : nil
    }

    // MARK: - Transform function list (`transform: …`)

    // Parses `{ "type": "functions", "list": [...] }` into TransformFn entries.
    // Each entry carries `"fn"` + axis-specific keys documented in
    // `examples/properties/transforms/transform-functions.json`.
    private static func applyTransform(_ data: IRValue, into agg: inout TransformsAggregate) {
        // Variant 1: `"none"` → empty list, no-op (but still "touched" so
        // the applier knows the property was present). The IR carries
        // `transform: none` as EITHER a bare string or a `{ "type":
        // "none" }` object (same dual carrier the web engine accepts in
        // TransformExtractor.ts) — handle both; the object form used to
        // fall through the Variant-2 guard untouched (caught by
        // TransformsTests' 'none' check).
        if data.stringValue == "none" { agg.touched = true; return }
        if case .object(let o0) = data, o0["type"]?.stringValue == "none" {
            agg.touched = true; return
        }
        // Variant 2: `{ "type": "functions", "list": […] }`.
        guard case .object(let o) = data,
              let list = o["list"]?.arrayValue else { return }
        // Iterate every function token and append the parsed TransformFn.
        for entry in list {
            guard case .object(let fn) = entry,
                  let name = fn["fn"]?.stringValue else { continue }
            if let parsed = parseFunction(name: name, fields: fn) {
                agg.functions.append(parsed)
                agg.touched = true
            }
        }
    }

    // Dispatch table for the CSS transform function palette. Defaults
    // missing axes to identity values so the applier never has to branch.
    private static func parseFunction(name: String, fields: [String: IRValue]) -> TransformFn? {
        // Helper — pixel length out of a `{ "px": N }` blob or nested length.
        func lenPx(_ k: String) -> CGFloat {
            CGFloat(extractLengthPx(fields[k]) ?? 0)
        }
        // Number / percentage → scalar (percentages arrive as 150 → 1.5).
        func scalar(_ k: String, default def: Double = 1) -> CGFloat {
            if let d = fields[k]?.doubleValue { return CGFloat(d) }
            return CGFloat(def)
        }
        // Degrees out of a `{ "deg": N }` angle blob.
        func deg(_ k: String, default def: Double = 0) -> CGFloat {
            guard let ang = extractAngle(fields[k]) else { return CGFloat(def) }
            return CGFloat(ang.degrees)
        }
        switch name {
        // ── translate family ───────────────────────────────────────────
        case "translate", "translate3d":
            return .translate(x: lenPx("x"), y: lenPx("y"), z: lenPx("z"))
        case "translateX": return .translate(x: lenPx("x"), y: 0, z: 0)
        case "translateY": return .translate(x: 0, y: lenPx("y"), z: 0)
        case "translateZ": return .translate(x: 0, y: 0, z: lenPx("z"))
        // ── scale family ───────────────────────────────────────────────
        case "scale", "scale3d":
            return .scale(x: scalar("x"), y: scalar("y"), z: scalar("z"))
        case "scaleX": return .scale(x: scalar("x"), y: 1, z: 1)
        case "scaleY": return .scale(x: 1, y: scalar("y"), z: 1)
        case "scaleZ": return .scale(x: 1, y: 1, z: scalar("z"))
        // ── rotate family ──────────────────────────────────────────────
        case "rotate": return .rotate(x: 0, y: 0, z: 1, deg: deg("a"))
        case "rotateX": return .rotate(x: 1, y: 0, z: 0, deg: deg("a"))
        case "rotateY": return .rotate(x: 0, y: 1, z: 0, deg: deg("a"))
        case "rotateZ": return .rotate(x: 0, y: 0, z: 1, deg: deg("a"))
        case "rotate3d":
            // 3D rotate carries (x, y, z, angle) as separate fields.
            return .rotate(x: scalar("x", default: 0),
                           y: scalar("y", default: 0),
                           z: scalar("z", default: 0),
                           deg: deg("a"))
        // ── skew family ────────────────────────────────────────────────
        case "skew":  return .skew(xDeg: deg("x"), yDeg: deg("y"))
        case "skewX": return .skew(xDeg: deg("x"), yDeg: 0)
        case "skewY": return .skew(xDeg: 0,        yDeg: deg("y"))
        // ── matrix ─────────────────────────────────────────────────────
        case "matrix":
            return .matrix(a: scalar("a"), b: scalar("b"), c: scalar("c"),
                           d: scalar("d"), e: scalar("e"), f: scalar("f"))
        case "matrix3d":
            // 3D matrix → collapse to 2D affine for SwiftUI: pull the 4
            // meaningful entries from the 4×4 column-major IR. That drops
            // the perspective column — documented limitation. 3D preview
            // requires CoreAnimation `CATransform3D` which isn't exposed
            // in SwiftUI modifier space.
            let a = scalar("a1"), b = scalar("b1")
            let c = scalar("a2"), d = scalar("b2")
            let e = scalar("a4"), f = scalar("b4")
            return .matrix(a: a, b: b, c: c, d: d, e: e, f: f)
        // ── perspective function (inline, not the longhand) ───────────
        case "perspective":
            // Wave 5: the parser emits the distance under "l" (see the
            // Kotlin transform parser's length field), not "d" — the old
            // read produced 0 for every perspective() and the wave-5
            // keystone gate (`d > 0`) never fired. Keep "d" as fallback
            // for older IR captures.
            return .perspective(d: fields["l"] != nil ? lenPx("l") : lenPx("d"))
        default:
            // Unknown function — drop silently so an exotic IR shape
            // doesn't break the whole transform chain.
            return nil
        }
    }

    // Small helper — pulls a `px` out of a `{ "px": N }` or wrapped shape.
    private static func extractLengthPx(_ v: IRValue?) -> Double? {
        guard let v = v else { return nil }
        if case .object(let o) = v {
            if let px = o["px"]?.doubleValue { return px }
            if let inner = o["length"], let d = extractLengthPx(inner) { return d }
        }
        if let d = v.doubleValue { return d }
        return nil
    }

    // MARK: - Rotate longhand

    // IR shapes:
    //   { "type": "none" }
    //   { "type": "angle", "deg": N }
    //   { "type": "axis-angle", "x": a, "y": b, "z": c, "angle": { "deg": N } }
    private static func applyRotate(_ data: IRValue, into agg: inout TransformsAggregate) {
        guard case .object(let o) = data else { return }
        switch o["type"]?.stringValue {
        case "none":
            // Explicit "no rotation" — still counts as touched.
            agg.rotate = .rotate(x: 0, y: 0, z: 1, deg: 0)
            agg.touched = true
        case "angle":
            if let deg = o["deg"]?.doubleValue {
                agg.rotate = .rotate(x: 0, y: 0, z: 1, deg: CGFloat(deg))
                agg.touched = true
            }
        case "axis-angle":
            let x = o["x"]?.doubleValue ?? 0
            let y = o["y"]?.doubleValue ?? 0
            let z = o["z"]?.doubleValue ?? 1
            // Nested angle in `angle` sub-blob.
            let deg = extractAngle(o["angle"])?.degrees ?? 0
            agg.rotate = .rotate(x: CGFloat(x), y: CGFloat(y), z: CGFloat(z),
                                 deg: CGFloat(deg))
            agg.touched = true
        default: break
        }
    }

    // MARK: - Scale longhand

    // IR shapes: "none", { "type": "uniform", "value": N }, { "type": "2d", x, y },
    // { "type": "3d", x, y, z }.
    private static func applyScale(_ data: IRValue, into agg: inout TransformsAggregate) {
        guard case .object(let o) = data else { return }
        switch o["type"]?.stringValue {
        case "none":
            agg.scale = .scale(x: 1, y: 1, z: 1)
            agg.touched = true
        case "uniform":
            let v = o["value"]?.doubleValue ?? 1
            agg.scale = .scale(x: CGFloat(v), y: CGFloat(v), z: 1)
            agg.touched = true
        case "2d":
            let x = o["x"]?.doubleValue ?? 1
            let y = o["y"]?.doubleValue ?? 1
            agg.scale = .scale(x: CGFloat(x), y: CGFloat(y), z: 1)
            agg.touched = true
        case "3d":
            let x = o["x"]?.doubleValue ?? 1
            let y = o["y"]?.doubleValue ?? 1
            let z = o["z"]?.doubleValue ?? 1
            agg.scale = .scale(x: CGFloat(x), y: CGFloat(y), z: CGFloat(z))
            agg.touched = true
        default: break
        }
    }

    // MARK: - Translate longhand

    // IR shapes (see translate-longhand.json):
    //   "none" | { "type": "length", "length": {px} } | { "type": "percentage", ... }
    //   | { "type": "2d", "x": {px|pct}, "y": {px|pct} }
    //   | { "type": "3d", "x", "y", "z" }
    private static func applyTranslate(_ data: IRValue, into agg: inout TransformsAggregate) {
        guard case .object(let o) = data else { return }
        switch o["type"]?.stringValue {
        case "none":
            agg.translate = .translate(x: 0, y: 0, z: 0)
            agg.touched = true
        case "length":
            let px = extractLengthPx(o["length"]) ?? 0
            agg.translate = .translate(x: CGFloat(px), y: 0, z: 0)
            agg.touched = true
        case "percentage":
            // CSS `translate: 50%` resolves to half the element's
            // width on the X axis (CSS Transforms 2 §3.2). The
            // GeometryReader-aware applier resolves at draw time.
            let pct = (o["percentage"]?.doubleValue ?? 0) / 100.0
            agg.translate = .translate(x: 0, y: 0, z: 0, xFrac: CGFloat(pct), yFrac: 0)
            agg.touched = true
        case "2d":
            let xPx = extractAxisPx(o["x"]) ?? 0
            let yPx = extractAxisPx(o["y"]) ?? 0
            let xPct = extractAxisPct(o["x"]) ?? 0
            let yPct = extractAxisPct(o["y"]) ?? 0
            agg.translate = .translate(x: CGFloat(xPx), y: CGFloat(yPx), z: 0,
                                       xFrac: CGFloat(xPct), yFrac: CGFloat(yPct))
            agg.touched = true
        case "3d":
            let x = extractAxisPx(o["x"]) ?? 0
            let y = extractAxisPx(o["y"]) ?? 0
            let z = extractLengthPx(o["z"]) ?? 0
            agg.translate = .translate(x: CGFloat(x), y: CGFloat(y), z: CGFloat(z))
            agg.touched = true
        default: break
        }
    }

    // 2d translate axes can be either `{ "type": "length", "px": N }` or
    // `{ "type": "percentage", "percentage": N }`. Percentages resolve to
    // 0 here (see TODO above); px flows through normally.
    private static func extractAxisPx(_ v: IRValue?) -> Double? {
        guard let v = v, case .object(let o) = v else { return nil }
        if o["type"]?.stringValue == "percentage" { return 0 }
        return extractLengthPx(v)
    }

    // Companion to `extractAxisPx` for the `translate:` longhand: returns
    // a 0..1 fraction when the axis was a CSS percentage (e.g.
    // `translate: 50% 50%`). The applier multiplies this by the
    // element's own width/height inside a GeometryReader.
    private static func extractAxisPct(_ v: IRValue?) -> Double? {
        guard let v = v, case .object(let o) = v else { return nil }
        if o["type"]?.stringValue != "percentage" { return nil }
        return (o["percentage"]?.doubleValue ?? 0) / 100.0
    }

    // MARK: - TransformOrigin

    // IR carries x / y (and optional z) sub-blobs. Each axis is either
    // { "type": "keyword", "value": "TOP|LEFT|..." }, { "type": "percentage", ... },
    // or { "type": "length", "px": N }.
    private static func applyOrigin(_ data: IRValue, into agg: inout TransformsAggregate) {
        guard case .object(let o) = data else { return }
        var origin = TransformOriginValue()
        // Defaults: CSS initial is 50% 50%, matched by UnitPoint.center.
        var fx: CGFloat = 0.5, fy: CGFloat = 0.5
        var xPx: CGFloat? = nil, yPx: CGFloat? = nil
        if let xBlob = o["x"] { parseOriginAxis(xBlob, isY: false, frac: &fx, px: &xPx) }
        if let yBlob = o["y"] { parseOriginAxis(yBlob, isY: true,  frac: &fy, px: &yPx) }
        origin.unit = UnitPoint(x: fx, y: fy)
        origin.xPx = xPx
        origin.yPx = yPx
        if let zBlob = o["z"], let z = extractLengthPx(zBlob) {
            origin.zPx = CGFloat(z)
        }
        agg.origin = origin
        agg.touched = true
    }

    // Maps one CSS axis value to (fraction, optional pixel override).
    // `isY` swaps the meaning of `top`/`bottom` vs `left`/`right` keywords.
    private static func parseOriginAxis(_ v: IRValue, isY: Bool,
                                        frac: inout CGFloat, px: inout CGFloat?) {
        guard case .object(let o) = v else { return }
        switch o["type"]?.stringValue {
        case "keyword":
            switch o["value"]?.stringValue?.uppercased() {
            case "LEFT":   frac = 0.0
            case "RIGHT":  frac = 1.0
            case "TOP":    frac = 0.0   // on Y axis
            case "BOTTOM": frac = 1.0
            case "CENTER": frac = 0.5
            default:       frac = 0.5
            }
        case "percentage":
            if let p = o["percentage"]?.doubleValue { frac = CGFloat(p / 100.0) }
        case "length":
            if let p = o["px"]?.doubleValue { px = CGFloat(p) }
        default: break
        }
        _ = isY  // Consumed via the caller's channel.
    }

    // MARK: - TransformBox / Style / Backface

    private static func applyBox(_ data: IRValue, into agg: inout TransformsAggregate) {
        // Keyword lives as a bare UPPERCASE string.
        guard let s = data.stringValue else { return }
        switch s {
        case "CONTENT_BOX": agg.box = .contentBox
        case "BORDER_BOX":  agg.box = .borderBox
        case "FILL_BOX":    agg.box = .fillBox
        case "STROKE_BOX":  agg.box = .strokeBox
        case "VIEW_BOX":    agg.box = .viewBox
        default: return
        }
        agg.touched = true
    }

    private static func applyStyle(_ data: IRValue, into agg: inout TransformsAggregate) {
        // `FLAT` | `PRESERVE_3D`.
        guard let s = data.stringValue else { return }
        agg.preserve3D = (s == "PRESERVE_3D")
        agg.touched = true
    }

    private static func applyBackface(_ data: IRValue, into agg: inout TransformsAggregate) {
        guard let s = data.stringValue else { return }
        agg.backfaceHidden = (s == "HIDDEN")
        agg.touched = true
    }

    // MARK: - Perspective + Perspective origin

    // `{ "type": "none" }` | `{ "type": "length", "px": N }`
    private static func applyPerspective(_ data: IRValue, into agg: inout TransformsAggregate) {
        guard case .object(let o) = data else { return }
        var p = agg.perspective ?? PerspectiveValue()
        switch o["type"]?.stringValue {
        case "none": p.distancePx = nil
        case "length":
            // `0` is documented as an alias for `0px`; SwiftUI treats 0
            // distance as degenerate so we clamp to nil in that case.
            if let px = o["px"]?.doubleValue, px > 0 { p.distancePx = CGFloat(px) }
            else { p.distancePx = nil }
        default: break
        }
        agg.perspective = p
        agg.touched = true
    }

    // PerspectiveOrigin — x/y axes similar to TransformOrigin but allow
    // a bare `{ "type": "center" }` shape as well.
    private static func applyPerspectiveOrigin(_ data: IRValue,
                                               into agg: inout TransformsAggregate) {
        guard case .object(let o) = data else { return }
        var fx: CGFloat = 0.5, fy: CGFloat = 0.5
        var unused: CGFloat? = nil
        if let xBlob = o["x"] { parsePerspectiveAxis(xBlob, frac: &fx, px: &unused) }
        if let yBlob = o["y"] { parsePerspectiveAxis(yBlob, frac: &fy, px: &unused) }
        var p = agg.perspective ?? PerspectiveValue()
        p.origin = UnitPoint(x: fx, y: fy)
        agg.perspective = p
        agg.touched = true
    }

    // Handles the extra `{ "type": "center" }` shape emitted by
    // PerspectiveOriginPropertyParser in addition to the usual trio.
    private static func parsePerspectiveAxis(_ v: IRValue,
                                             frac: inout CGFloat,
                                             px: inout CGFloat?) {
        guard case .object(let o) = v else { return }
        if o["type"]?.stringValue == "center" { frac = 0.5; return }
        parseOriginAxis(v, isY: false, frac: &frac, px: &px)
    }
}
