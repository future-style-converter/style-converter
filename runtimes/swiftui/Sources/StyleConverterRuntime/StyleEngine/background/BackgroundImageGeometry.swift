//
//  BackgroundImageGeometry.swift
//  StyleEngine/background — wave 8 (issue #36: url() raster layers).
//
//  PURE geometry for painting one raster background layer: image size +
//  box size + background-size/-position/-repeat → the drawn tile size,
//  the anchor tile's origin, and the tile enumeration. Split from the
//  rendering view so XCTest pins cover/contain/px/percent scaling, the
//  position anchor math and the repeat lattice without touching SwiftUI.
//
//  Spec map (css-backgrounds-3):
//    §3.9 background-size  — cover / contain / auto / <length-percentage>
//    §3.6 background-position — keyword/percent anchor: offset =
//         (box − tile) × fraction; px offsets measure from the top/left
//    §3.4 background-repeat — repeat/no-repeat per axis; `space` and
//         `round` are approximated as plain repeat + one log (v1 —
//         documented, never silent)
//

import Foundation
import CoreGraphics

/// Namespace for the pure layer-geometry math.
enum BackgroundImageGeometry {

    /// Resolved paint plan for one raster layer inside one box.
    struct Placement: Equatable {
        /// Drawn size of ONE image copy (px).
        var tileSize: CGSize
        /// Top-left of the ANCHOR copy (the one background-position
        /// places; the repeat lattice extends from it in both directions).
        var origin: CGPoint
        /// Tile along the x axis (repeat / space / round on that axis).
        var repeatX: Bool
        /// Tile along the y axis.
        var repeatY: Bool
    }

    // MARK: - Size (§3.9)

    /// Drawn size of one copy. `nil` size layer = the CSS initial `auto`
    /// (intrinsic pixels, 1 image px = 1 CSS px at the harness's 1×).
    static func tileSize(imageSize: CGSize, box: CGSize,
                         size: BackgroundSizeLayer?) -> CGSize {
        // Degenerate images can't scale meaningfully.
        guard imageSize.width > 0, imageSize.height > 0 else { return imageSize }
        switch size ?? .auto {
        case .auto:
            // Intrinsic dimensions (§3.9 `auto auto`).
            return imageSize
        case .cover:
            // Smallest size covering the box while preserving ratio.
            let s = max(box.width / imageSize.width, box.height / imageSize.height)
            return CGSize(width: imageSize.width * s, height: imageSize.height * s)
        case .contain:
            // Largest size fitting inside the box while preserving ratio.
            let s = min(box.width / imageSize.width, box.height / imageSize.height)
            return CGSize(width: imageSize.width * s, height: imageSize.height * s)
        case .explicit(let w, let h):
            // Per-axis resolution; a single `auto` axis preserves the
            // intrinsic ratio against the resolved one (§3.9).
            let rw = resolveDim(w, boxAxis: box.width)
            let rh = resolveDim(h, boxAxis: box.height)
            switch (rw, rh) {
            case (let x?, let y?): return CGSize(width: x, height: y)
            case (let x?, nil):    return CGSize(width: x, height: x * imageSize.height / imageSize.width)
            case (nil, let y?):    return CGSize(width: y * imageSize.width / imageSize.height, height: y)
            case (nil, nil):       return imageSize
            }
        }
    }

    /// One explicit axis: px literal or percent of the box axis; auto = nil.
    private static func resolveDim(_ d: BackgroundSizeDim, boxAxis: CGFloat) -> CGFloat? {
        switch d {
        case .auto:              return nil
        case .px(let v):         return CGFloat(v)
        case .percent(let pct):  return boxAxis * CGFloat(pct) / 100.0
        }
    }

    // MARK: - Position (§3.6)

    /// Anchor-tile origin. Keyword/percent positions resolve as
    /// `(box − tile) × fraction` — the CSS edge-alignment identity (50%
    /// centers, 100% flush-right/bottom); px offsets are plain insets
    /// from the top-left. Absent axes default to 0% (the CSS initial).
    static func origin(tile: CGSize, box: CGSize,
                       x: BackgroundAxisPosition?, y: BackgroundAxisPosition?) -> CGPoint {
        CGPoint(x: axisOffset(tile: tile.width, box: box.width, pos: x, isX: true),
                y: axisOffset(tile: tile.height, box: box.height, pos: y, isX: false))
    }

    /// One axis of the anchor origin.
    private static func axisOffset(tile: CGFloat, box: CGFloat,
                                   pos: BackgroundAxisPosition?, isX: Bool) -> CGFloat {
        switch pos {
        case nil:
            return 0 // initial 0% — top/left flush
        case .px(let v):
            return CGFloat(v)
        case .percent(let pct):
            return (box - tile) * CGFloat(pct) / 100.0
        case .keyword(let kw):
            // Parser normalizes to upper-case labels; tolerate case.
            switch kw.uppercased() {
            case "LEFT":   return 0
            case "TOP":    return 0
            case "RIGHT":  return box - tile
            case "BOTTOM": return box - tile
            case "CENTER": return (box - tile) / 2
            default:
                // Unknown keyword — treat as the axis initial (0%) and
                // leave the breadcrumb; never guess a fraction.
                PropertyTracker.logOnce(
                    key: "bg-position-kw:\(kw)",
                    message: "background-position keyword '\(kw)' not recognized — using 0% (axis \(isX ? "x" : "y"))")
                return 0
            }
        }
    }

    // MARK: - Repeat lattice (§3.4)

    /// Repeat flags for one layer. `space`/`round` approximate to plain
    /// repeat (logged once) — implementing their gap/rescale arithmetic
    /// is deferred until a fixture exercises them.
    static func repeatFlags(_ layer: BackgroundRepeatLayer?) -> (x: Bool, y: Bool) {
        func axis(_ kw: String?) -> Bool {
            switch (kw ?? "repeat").lowercased() {
            case "no-repeat": return false
            case "repeat":    return true
            case "space", "round":
                PropertyTracker.logOnce(
                    key: "bg-repeat:\(kw ?? "")",
                    message: "background-repeat '\(kw ?? "")' approximated as 'repeat' (wave-8 v1 — gap/rescale arithmetic deferred)")
                return true
            default:          return true // CSS initial is repeat
            }
        }
        return (axis(layer?.x), axis(layer?.y))
    }

    /// Full placement for one layer.
    static func placement(imageSize: CGSize, box: CGSize,
                          size: BackgroundSizeLayer?,
                          positionX: BackgroundAxisPosition?,
                          positionY: BackgroundAxisPosition?,
                          repeatLayer: BackgroundRepeatLayer?) -> Placement {
        let tile = tileSize(imageSize: imageSize, box: box, size: size)
        let anchor = origin(tile: tile, box: box, x: positionX, y: positionY)
        let rep = repeatFlags(repeatLayer)
        return Placement(tileSize: tile, origin: anchor, repeatX: rep.x, repeatY: rep.y)
    }

    /// Defensive ceiling on enumerated tiles — a 1×1 image tiling a full
    /// canvas would enumerate ~330k rects and stall the Canvas. Callers
    /// switch to a shading-based fill above this (see the renderer).
    static let tileCap = 4096

    /// Enumerate the tile rects covering `box`. The lattice extends from
    /// the anchor origin in both directions on repeating axes (CSS tiles
    /// infinitely; only the copies intersecting the paint area draw).
    /// Truncated at `tileCap` (callers pre-check `tileCount`).
    static func tileRects(placement p: Placement, box: CGSize) -> [CGRect] {
        // Zero-sized tiles paint nothing (avoid an infinite lattice).
        guard p.tileSize.width > 0, p.tileSize.height > 0 else { return [] }
        // First lattice coordinate ≤ 0 on each repeating axis.
        func starts(anchor: CGFloat, tile: CGFloat, extent: CGFloat, repeats: Bool) -> [CGFloat] {
            guard repeats else { return [anchor] }
            // k = smallest integer with anchor + k·tile > −tile.
            let k = ((-anchor - tile) / tile).rounded(.up)
            var out: [CGFloat] = []
            var v = anchor + k * tile
            while v < extent {
                if v + tile > 0 { out.append(v) }
                v += tile
                if out.count > tileCap { break } // hard stop — cap guard
            }
            return out
        }
        let xs = starts(anchor: p.origin.x, tile: p.tileSize.width, extent: box.width, repeats: p.repeatX)
        let ys = starts(anchor: p.origin.y, tile: p.tileSize.height, extent: box.height, repeats: p.repeatY)
        var rects: [CGRect] = []
        rects.reserveCapacity(min(xs.count * ys.count, tileCap))
        for y in ys {
            for x in xs {
                rects.append(CGRect(x: x, y: y, width: p.tileSize.width, height: p.tileSize.height))
                if rects.count >= tileCap { return rects }
            }
        }
        return rects
    }

    /// Cheap tile-count estimate for the cap pre-check.
    static func tileCount(placement p: Placement, box: CGSize) -> Int {
        guard p.tileSize.width > 0, p.tileSize.height > 0 else { return 0 }
        let nx = p.repeatX ? Int((box.width / p.tileSize.width).rounded(.up)) + 1 : 1
        let ny = p.repeatY ? Int((box.height / p.tileSize.height).rounded(.up)) + 1 : 1
        return nx * ny
    }
}
