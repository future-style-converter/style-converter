//
//  MotionOffsetExtractor.swift
//  StyleEngine/layout/advanced — fidelity wave 2.
//
//  IR → MotionOffsetConfig for the CSS Motion Path family. IR shapes
//  (converter parsers under parsing/css/properties/longhands/layout/):
//    OffsetPath      {"type":"ray","deg":45.0} | {"type":"none"} | path/…
//    OffsetPosition  {"type":"position","x":{"type":"left"},"y":{…}}
//    OffsetRotate    {"type":"auto"|"reverse"} | {"type":"angle","deg":N}
//    OffsetDistance  {"px":N} | {"type":"percentage","value":N}
//

import Foundation
import CoreGraphics

enum MotionOffsetExtractor {

    /// Single pass over the property list. Returns nil when no renderable
    /// offset-path arrived (family absent, `none`, or unsupported shape)
    /// so StyleBuilder can skip the applier entirely.
    static func extract(from properties: [IRProperty]) -> MotionOffsetConfig? {
        var cfg = MotionOffsetConfig()
        for prop in properties {
            switch prop.type {
            case "OffsetPath":
                // Only ray() is renderable; path()/shapes/url() log as
                // unsupported by leaving `path` nil (honest no-op).
                if case .object(let o) = prop.data,
                   o["type"]?.stringValue?.lowercased() == "ray",
                   let deg = o["deg"]?.doubleValue {
                    cfg.path = .ray(deg: CGFloat(deg))
                }
            case "OffsetPosition":
                // `<position>` pair — keywords / px / percent per axis.
                if case .object(let o) = prop.data,
                   o["type"]?.stringValue == "position" {
                    if let x = axis(o["x"], horizontal: true) { cfg.positionX = x }
                    if let y = axis(o["y"], horizontal: false) { cfg.positionY = y }
                }
            case "OffsetRotate":
                if case .object(let o) = prop.data {
                    switch o["type"]?.stringValue?.lowercased() {
                    case "auto":    cfg.rotate = .auto
                    case "reverse": cfg.rotate = .reverse
                    case "angle":
                        if let d = o["deg"]?.doubleValue { cfg.rotate = .fixed(deg: CGFloat(d)) }
                    default: break
                    }
                }
            case "OffsetDistance":
                // Absolute px only — percent needs the ray's 100% basis
                // (containing-block corner distance), deferred.
                if let px = ValueExtractors.extractPx(prop.data) {
                    cfg.distancePx = px
                }
            default:
                break // not a motion-path property
            }
        }
        // Renderable only when a supported path shape arrived.
        return cfg.path != nil ? cfg : nil
    }

    /// One `<position>` axis payload → MotionAxis. Mirrors the web
    /// engine's axisToken (_offset_shared.ts) shape handling.
    private static func axis(_ v: IRValue?, horizontal: Bool) -> MotionAxis? {
        guard case .object(let o)? = v else { return nil }
        switch o["type"]?.stringValue?.lowercased() {
        case "left", "top":      return .start
        case "right", "bottom":  return .end
        case "center":           return .center
        case "length":
            if let px = o["px"]?.doubleValue { return .px(CGFloat(px)) }
            return nil
        case "percentage":
            if let p = o["value"]?.doubleValue { return .percent(CGFloat(p)) }
            return nil
        default:
            return nil
        }
    }
}
