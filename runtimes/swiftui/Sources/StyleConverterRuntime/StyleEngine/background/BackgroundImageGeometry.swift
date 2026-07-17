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
//    §3.7 background-repeat — repeat/no-repeat/space/round per axis; the
//         real space (whole tiles + equal gaps) and round (integer-count
//         rescale) arithmetic lives in BackgroundTileMath (ported from
//         the Compose runtime's BackgroundTileMath.kt), replacing the
//         wave-8 "approximated as repeat" logOnce path
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
        /// x-axis repeat mode (§3.7) — `round` may rescale the drawn
        /// tile width when the lattice is enumerated (tileRects).
        var repeatX: AxisRepeat
        /// y-axis repeat mode.
        var repeatY: AxisRepeat
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

    /// Drawn size of one GRADIENT copy. Gradients have no intrinsic
    /// dimensions or proportions, so §3.9 resolves `auto` to the
    /// positioning area's axis, and cover/contain — both defined via the
    /// (absent) intrinsic ratio — degenerate to the area as well. Only
    /// explicit lengths/percents shrink or stretch the tile; an `auto`
    /// axis inside an explicit pair is 100% of the area for the same
    /// no-intrinsic-proportions reason (NOT the raster ratio-preserving
    /// branch in `tileSize` above).
    static func gradientTileSize(box: CGSize, size: BackgroundSizeLayer?) -> CGSize {
        switch size ?? .auto {
        case .auto, .cover, .contain:
            // Intrinsic-less image: every keyword fills the area (§3.9).
            return box
        case .explicit(let w, let h):
            // Per-axis resolution; auto falls back to the box axis.
            return CGSize(width: resolveDim(w, boxAxis: box.width) ?? box.width,
                          height: resolveDim(h, boxAxis: box.height) ?? box.height)
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

    // MARK: - Repeat lattice (§3.7)

    /// Per-axis repeat modes for one layer (nil layer / axis = the CSS
    /// initial `repeat`). The wave-8 "space/round approximated as
    /// repeat" logOnce path is retired: BackgroundTileMath implements
    /// the real §3.7 gap/rescale arithmetic these modes select.
    static func repeatModes(_ layer: BackgroundRepeatLayer?) -> (x: AxisRepeat, y: AxisRepeat) {
        (BackgroundTileMath.mode(layer?.x), BackgroundTileMath.mode(layer?.y))
    }

    /// Full placement for one layer.
    static func placement(imageSize: CGSize, box: CGSize,
                          size: BackgroundSizeLayer?,
                          positionX: BackgroundAxisPosition?,
                          positionY: BackgroundAxisPosition?,
                          repeatLayer: BackgroundRepeatLayer?) -> Placement {
        let tile = tileSize(imageSize: imageSize, box: box, size: size)
        let anchor = origin(tile: tile, box: box, x: positionX, y: positionY)
        let rep = repeatModes(repeatLayer)
        return Placement(tileSize: tile, origin: anchor, repeatX: rep.x, repeatY: rep.y)
    }

    /// Defensive ceiling on enumerated tiles — a 1×1 image tiling a full
    /// canvas would enumerate ~330k rects and stall the Canvas. Callers
    /// switch to a shading-based fill above this (see the renderer).
    static let tileCap = 4096

    /// Enumerate the tile rects covering `box` as the cartesian product
    /// of one BackgroundTileMath axis plan per axis (§3.7: each axis
    /// tiles independently; `round` may rescale its axis's tile size).
    /// `repeat` lattices extend past the box edges (CSS tiles infinitely
    /// and clips to the painting area — PAINTERS must clip to the box).
    /// Truncated at `tileCap` (callers pre-check `tileCount`).
    static func tileRects(placement p: Placement, box: CGSize) -> [CGRect] {
        // Zero-sized tiles paint nothing (avoid an infinite lattice).
        guard p.tileSize.width > 0, p.tileSize.height > 0 else { return [] }
        // Independent per-axis plans (§3.7 two-keyword grammar).
        let px = BackgroundTileMath.axisPlan(area: box.width, tile: p.tileSize.width,
                                             anchor: p.origin.x, mode: p.repeatX)
        let py = BackgroundTileMath.axisPlan(area: box.height, tile: p.tileSize.height,
                                             anchor: p.origin.y, mode: p.repeatY)
        var rects: [CGRect] = []
        rects.reserveCapacity(min(px.origins.count * py.origins.count, tileCap))
        for y in py.origins {
            for x in px.origins {
                // Axis-plan tile sizes (not p.tileSize) so `round` on
                // one axis rescales only that axis's extent.
                rects.append(CGRect(x: x, y: y, width: px.tileSize, height: py.tileSize))
                if rects.count >= tileCap { return rects } // hard stop — cap guard
            }
        }
        return rects
    }

    /// Cheap tile-count estimate for the cap pre-check — mirrors the
    /// axisPlan counts without materializing the origin lists.
    static func tileCount(placement p: Placement, box: CGSize) -> Int {
        guard p.tileSize.width > 0, p.tileSize.height > 0 else { return 0 }
        func axis(_ area: CGFloat, _ tile: CGFloat, _ mode: AxisRepeat) -> Int {
            switch mode {
            // Single anchored copy.
            case .noRepeat: return 1
            // Phase shift adds at most one extra edge tile.
            case .repeat:   return Int((area / tile).rounded(.up)) + 1
            // Whole tiles only — but never below the single-tile fallback.
            case .space:    return max(Int(floor(area / tile)), 1)
            // Integer refit, never fewer than one tile.
            case .round:    return max(Int((area / tile).rounded()), 1)
            }
        }
        return axis(box.width, p.tileSize.width, p.repeatX)
            * axis(box.height, p.tileSize.height, p.repeatY)
    }
}
