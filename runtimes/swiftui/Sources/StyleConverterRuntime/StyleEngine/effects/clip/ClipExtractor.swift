//
//  ClipExtractor.swift
//  StyleEngine/effects/clip — Phase 8.
//
//  Parses the effects clip family into a single ClipConfig. Owned
//  properties: ClipPath, ClipRule, Clip (legacy), ClipPathGeometryBox
//  (geometry-box rides inside ClipPath today so this name is reserved
//  for future standalone use). See ClipPathPropertyParser.kt for the
//  full value flavour list.
//

import Foundation

enum ClipProperty {
    // Centralises owned property names so PropertyRegistry + self-test
    // can union from one spot.
    static let set: Set<String> = [
        "ClipPath", "ClipRule", "Clip", "ClipPathGeometryBox",
    ]
}

enum ClipExtractor {

    // Top-level — returns nil when no owned property was present.
    static func extract(from properties: [IRProperty]) -> ClipConfig? {
        var cfg = ClipConfig()
        for p in properties {
            switch p.type {
            case "ClipPath":             applyClipPath(p.data, into: &cfg)
            case "ClipRule":             applyRule(p.data, into: &cfg)
            case "Clip":                 applyLegacy(p.data, into: &cfg)
            case "ClipPathGeometryBox":  applyGeometryBox(p.data, into: &cfg)
            default: break
            }
        }
        return cfg.touched ? cfg : nil
    }

    // MARK: - ClipPath

    // IR shapes documented in examples/properties/effects/clip-path-*.
    private static func applyClipPath(_ data: IRValue, into cfg: inout ClipConfig) {
        // Bare string shapes: "none" or "#svgId".
        if let s = data.stringValue {
            if s == "none" { cfg.shape = .none; cfg.touched = true; return }
            if s.hasPrefix("#") {
                cfg.shape = .url(id: String(s.dropFirst()))
                cfg.touched = true
                return
            }
            return
        }
        guard case .object(let o) = data else { return }

        // Geometry-box-only (or box + shape combo).
        if let box = o["geometry-box"]?.stringValue {
            // When a shape is also present, parse it; the box tells us
            // which reference rectangle the shape's units are in — we
            // don't currently honour that and always use the view's own
            // frame (documented TODO).
            if let shape = o["shape"], case .object(let inner) = shape {
                cfg.shape = parseShape(inner)
            } else {
                cfg.shape = .geometryBoxOnly(box: box)
            }
            cfg.touched = true
            return
        }

        // Bare-shape forms.
        cfg.shape = parseShape(o)
        cfg.touched = true
    }

    // Dispatcher for `{ "type": "inset|circle|ellipse|polygon|path|rect|xywh", ... }`.
    private static func parseShape(_ o: [String: IRValue]) -> ClipShape? {
        guard let t = o["type"]?.stringValue else { return nil }
        switch t {
        case "inset":
            // Four side lengths + optional "round" corner radius. The
            // `round` value can be either an absolute length (px) or a
            // CSS percentage. Per CSS Backgrounds 3 §5.2 a percentage
            // resolves to that fraction of the box's *width* on the X
            // axis and *height* on the Y axis — but the SwiftUI Path
            // call site uses a single radius, so we pass the fraction
            // through and let the InsetShape pick min(width,height) at
            // draw time (matches the prior simplification on Android).
            let roundBlob = o["round"]
            let roundPx: CGFloat = lenPx(roundBlob).map { CGFloat($0) } ?? 0
            // Pull a percentage radius out using the same accessor
            // pattern the rest of this file uses (stringValue /
            // doubleValue / objectValue) — earlier sketch reached for
            // pattern-binds that don't exist on this IRValue type.
            let roundPct: CGFloat? = {
                guard let rb = roundBlob?.objectValue,
                      let orig = rb["original"]?.objectValue,
                      orig["u"]?.stringValue == "PERCENT",
                      let v = orig["v"]?.doubleValue else { return nil }
                return CGFloat(v) / 100.0
            }()
            return .inset(top:    CGFloat(lenPx(o["t"]) ?? 0),
                          right:  CGFloat(lenPx(o["r"]) ?? 0),
                          bottom: CGFloat(lenPx(o["b"]) ?? 0),
                          left:   CGFloat(lenPx(o["l"]) ?? 0),
                          cornerRadius: roundPx,
                          cornerRadiusFraction: roundPct)
        case "circle":
            // Four shapes can land here, all from ClipPathShapeSerializer
            // (src/main/kotlin/app/irmodels/properties/effects/ClipPathSerializers.kt:63-67)
            // post-deepFlatten (src/main/kotlin/app/irmodels/IRPropertySerializer.kt:154-188):
            //   • flattened px:        `{ type:"circle", px:N }`
            //   • flattened percent:   `{ type:"circle", original:{v:N,u:"PERCENT"} }`
            //   • wrapped length:      `{ type:"circle", r:{...IRLength}, pos?:{x,y} }`
            //   • wrapped keyword:     `{ type:"circle", r:"closest-side"|"farthest-side", pos?:{x,y} }`
            //
            // The keyword form is the new variant added in swarm-003
            // clip-path-borderBox-1a — `circle(farthest-side)` previously
            // dropped the keyword at parse and arrived here as
            // `{type:"circle"}` with no radius field at all. Now the
            // serializer routes the keyword as a JSON string primitive
            // at the `r` key, which we discriminate via `stringValue` /
            // `objectValue` below.
            if let px = o["px"]?.doubleValue {
                // Flattened-px form. Center defaults to (0.5, 0.5) since
                // deepFlatten only fires when there's no `pos` sibling.
                return .circle(radius: CGFloat(px), radiusKind: .length,
                               cx: 0.5, cy: 0.5)
            }
            if let orig = o["original"]?.objectValue,
               let vv = orig["v"]?.doubleValue,
               orig["u"]?.stringValue == "PERCENT" {
                // Flattened-percent form. Same default-center reasoning as above.
                return .circle(radius: CGFloat(vv / 100.0), radiusKind: .percent,
                               cx: 0.5, cy: 0.5)
            }
            // Wrapped form (multi-field, deepFlatten skipped). Could be
            // either a length object OR a keyword string primitive.
            let (r, kind) = extractShapeRadius(o["r"])
            let (cx, cy) = extractPosition(o["pos"])
            return .circle(radius: CGFloat(r), radiusKind: kind,
                           cx: cx, cy: cy)
        case "ellipse":
            // Per-axis dispatch — each axis can independently be length,
            // percent, or keyword.
            let (rx, rxKind) = extractShapeRadius(o["rx"])
            let (ry, ryKind) = extractShapeRadius(o["ry"])
            let (cx, cy) = extractPosition(o["pos"])
            return .ellipse(rx: CGFloat(rx), ry: CGFloat(ry),
                            rxKind: rxKind, ryKind: ryKind,
                            cx: cx, cy: cy)
        case "polygon":
            // Per CSS Shapes 2 §3.1.4 a polygon vertex is a
            // `<length-percentage>` pair. IRLengthPercentageSerializer
            // emits an axis as either a raw JSON number (percentage,
            // legacy compat shape) or an IRLength object such as
            // `{"px": 100}` (new length form — unlocks WPT fixtures
            // like clip-path-blending-offset which use px coordinates).
            // We dispatch on IRValue's case per axis so old percent
            // polygons keep byte-identical behaviour and new mixed-unit
            // polygons render correctly.
            guard let pts = o["points"]?.arrayValue else { return nil }
            let points = pts.compactMap { pv -> PolygonPoint? in
                guard case .object(let p) = pv,
                      let xAxis = polygonAxis(p["x"]),
                      let yAxis = polygonAxis(p["y"]) else { return nil }
                return PolygonPoint(x: xAxis, y: yAxis)
            }
            return .polygon(points: points)
        case "path":
            return .path(data: o["d"]?.stringValue ?? "")
        case "rect":
            // top/right/bottom/left — "auto" allowed, else a length.
            return .rect(top:    autoOrPx(o["t"]),
                         right:  autoOrPx(o["r"]),
                         bottom: autoOrPx(o["b"]),
                         left:   autoOrPx(o["l"]),
                         cornerRadius: CGFloat(lenPx(o["round"]) ?? 0))
        case "xywh":
            return .xywh(x: CGFloat(lenPx(o["x"]) ?? 0),
                         y: CGFloat(lenPx(o["y"]) ?? 0),
                         w: CGFloat(lenPx(o["w"]) ?? 0),
                         h: CGFloat(lenPx(o["h"]) ?? 0),
                         cornerRadius: CGFloat(lenPx(o["round"]) ?? 0))
        default:
            return nil
        }
    }

    // ShapeRadius extraction — CSS Shapes 1 §3.1 `<shape-radius>` =
    // `<length-percentage> | closest-side | farthest-side`. Three wire
    // forms produced by ShapeRadiusSerializer (src/main/kotlin/app/
    // irmodels/properties/effects/ClipPathSerializers.kt) land here:
    //   - JSON string primitive ("closest-side" / "farthest-side") →
    //     keyword variant. Returns radius=0 because the resolution
    //     happens against the rendered rect (closest- and farthest-side
    //     are computed in CircleClip / EllipseClip at draw time).
    //   - JSON object with `px` → absolute length in points.
    //   - JSON object with `original.{v, u:"PERCENT"}` → percentage as a
    //     0…1 fraction.
    //   - Anything else / missing → default to ClosestSide so the
    //     resulting clip still renders sensibly (matches CSS spec
    //     default for an omitted radius).
    private static func extractShapeRadius(_ v: IRValue?) -> (Double, ShapeRadiusKind) {
        guard let v = v else { return (0, .closestSide) }
        // Keyword branch — JSON string primitive at the `r` key.
        if let s = v.stringValue {
            switch s.lowercased() {
            case "closest-side": return (0, .closestSide)
            case "farthest-side": return (0, .farthestSide)
            default:
                // Unknown keyword — fall through to the spec default
                // rather than silently rendering a 0-radius clip.
                return (0, .closestSide)
            }
        }
        // Length / percent branch — IRLength JSON object.
        guard case .object(let o) = v else { return (0, .closestSide) }
        if let px = o["px"]?.doubleValue { return (px, .length) }
        if let orig = o["original"]?.objectValue,
           let vv = orig["v"]?.doubleValue,
           orig["u"]?.stringValue == "PERCENT" { return (vv / 100.0, .percent) }
        return (0, .closestSide)
    }

    // Position extraction for circle/ellipse `at cx cy`. Returns unit-space.
    private static func extractPosition(_ v: IRValue?) -> (CGFloat, CGFloat) {
        guard let v = v, case .object(let o) = v else { return (0.5, 0.5) }
        // Each axis: length → nil fallback to 0.5 (no view metrics here),
        // or percent → fraction. We store length as a raw px-to-fraction
        // ratio by assuming 100px≈100% which is wrong; documented TODO.
        let x = axisFraction(o["x"]) ?? 0.5
        let y = axisFraction(o["y"]) ?? 0.5
        return (CGFloat(x), CGFloat(y))
    }

    private static func axisFraction(_ v: IRValue?) -> Double? {
        guard let v = v, case .object(let o) = v else { return nil }
        if let orig = o["original"]?.objectValue,
           let vv = orig["v"]?.doubleValue,
           orig["u"]?.stringValue == "PERCENT" { return vv / 100.0 }
        // Best-effort: a plain px turns into nil here → caller uses 0.5.
        return nil
    }

    /// Decode a single polygon axis into a [PolygonAxis] case. Mirrors the
    /// per-axis dispatch in the web (`ClipPathExtractor.ts`) and Android
    /// (`ClipPathExtractor.kt`) engines so the three platforms stay in
    /// lock-step on this wire format:
    ///  - `.int` / `.double` primitive → percentage (legacy form,
    ///    matches the pre-fix `{x:50.0, y:50.0}` shape)
    ///  - `.object` with `px` key → absolute length in points
    ///  - `.object` with `original.{v,u==PERCENT}` → percentage from
    ///    an IRLength wrapper (covers the corner case where the parser
    ///    routes a percent through Length(IRLength.PERCENT))
    /// Returns nil only when the axis JSON is missing/unreadable; the
    /// caller drops the whole vertex.
    private static func polygonAxis(_ v: IRValue?) -> PolygonAxis? {
        guard let v = v else { return nil }
        switch v {
        case .int(let i):     return .percent(CGFloat(i))
        case .double(let d):  return .percent(CGFloat(d))
        case .object(let o):
            // Absolute pixel length — the new form.
            if let px = o["px"]?.doubleValue {
                return .length(CGFloat(px))
            }
            // Wrapped-percent length (defensive — extracts `50%` if the
            // IR routed it through IRLength rather than IRPercentage).
            if let orig = o["original"]?.objectValue,
               let vv = orig["v"]?.doubleValue,
               orig["u"]?.stringValue == "PERCENT" {
                return .percent(CGFloat(vv))
            }
            // Other relative units (em/rem/vw…): no resolution without
            // runtime metrics, degrade to 0 length so neighbouring
            // vertices still render correctly.
            return .length(0)
        default:
            return nil
        }
    }

    // Rect allows literal "auto" in any component → nil; else a length.
    private static func autoOrPx(_ v: IRValue?) -> CGFloat? {
        if v?.stringValue == "auto" { return nil }
        return lenPx(v).map { CGFloat($0) }
    }

    // Small shared helper for `{ "px": N }` + nested wrapper shape.
    private static func lenPx(_ v: IRValue?) -> Double? {
        guard let v = v else { return nil }
        if case .object(let o) = v {
            if let d = o["px"]?.doubleValue { return d }
        }
        return v.doubleValue
    }

    // MARK: - ClipRule

    private static func applyRule(_ data: IRValue, into cfg: inout ClipConfig) {
        guard let s = data.stringValue else { return }
        // Qualify explicitly — `ClipRule.none` doesn't exist but a future
        // enum could add it. Explicit name prevents the Optional `.none`
        // pitfall from creeping in if the enum grows.
        switch s {
        case "NONZERO": cfg.rule = ClipRule.nonzero
        case "EVENODD": cfg.rule = ClipRule.evenodd
        default: return
        }
        cfg.touched = true
    }

    // MARK: - Legacy `clip`

    private static func applyLegacy(_ data: IRValue, into cfg: inout ClipConfig) {
        guard case .object(let o) = data else { return }
        switch o["type"]?.stringValue {
        case "auto":
            cfg.legacy = .auto
            cfg.touched = true
        case "rect":
            // Missing key in the IR means CSS `auto` for that edge
            // (see ClipPropertyParser.kt — `auto` → null). Pass `nil`
            // through so the applier can resolve against rect.size.
            func optPx(_ k: String) -> CGFloat? {
                return o[k].flatMap { lenPx($0).map { CGFloat($0) } }
            }
            cfg.legacy = .rect(
                top:    optPx("top"),
                right:  optPx("right"),
                bottom: optPx("bottom"),
                left:   optPx("left"))
            cfg.touched = true
        default: break
        }
    }

    // MARK: - ClipPathGeometryBox (reserved; parser folds this into ClipPath today)

    private static func applyGeometryBox(_ data: IRValue, into cfg: inout ClipConfig) {
        // Only reached if the IR emits a standalone property — currently
        // the parser folds geometry-box into ClipPath. We touch the
        // config so the self-test can observe presence.
        if let s = data.stringValue {
            cfg.shape = .geometryBoxOnly(box: s.lowercased())
            cfg.touched = true
        }
    }
}
