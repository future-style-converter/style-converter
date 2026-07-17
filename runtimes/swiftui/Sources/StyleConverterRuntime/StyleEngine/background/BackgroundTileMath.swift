//
//  BackgroundTileMath.swift
//  StyleEngine/background — gradient-geometry lane (space/round repeat).
//
//  css-backgrounds-3 §3.7 background-repeat tile placement — PURE math,
//  no SwiftUI types, ported 1:1 from the Compose runtime's
//  BackgroundTileMath.kt so both native platforms pin the same numbers
//  (XCTest mirror: BackgroundTileMathTests ↔ BackgroundTileMathTest.kt).
//  Callers build one AxisPlan per axis and draw the cartesian product of
//  the two origin lists (BackgroundImageGeometry.tileRects).
//

import Foundation
import CoreGraphics

/// One axis's background-repeat mode (§3.7 single-axis vocabulary).
/// Replaces the wave-8 Bool flags: `space`/`round` are real modes now,
/// no longer approximated as plain `repeat`.
enum AxisRepeat: Equatable {
    /// Single tile at the background-position anchor (§3.6).
    case noRepeat
    /// Edge-to-edge tiling phase-shifted through the anchor (§3.6:
    /// position anchors the infinite tiling pattern).
    case `repeat`
    /// Whole tiles + equal gaps, first/last flush with the edges (§3.7).
    case space
    /// Tile rescaled so a whole number fits the area exactly (§3.7).
    case round
}

/// Namespace for the pure axis-tiling arithmetic.
enum BackgroundTileMath {

    /// The resolved tiling of ONE axis: the (possibly `round`-rescaled)
    /// tile size plus every tile's start offset inside the positioning
    /// area. Empty `origins` = nothing to draw (degenerate tile/area).
    struct AxisPlan: Equatable {
        /// Tile extent along this axis — differs from the input only
        /// for `round`, which refits it to an integer count.
        var tileSize: CGFloat
        /// Start offset of every tile along this axis.
        var origins: [CGFloat]
    }

    /// Parsed repeat keyword → axis mode. The extractor normalizes to
    /// lowercase, but we re-lowercase defensively; unknown words take
    /// the CSS initial `repeat` (same policy as the wave-8 flags — the
    /// parser only ever emits the four §3.7 keywords, so anything else
    /// is wire noise best normalized rather than dropped).
    static func mode(_ keyword: String?) -> AxisRepeat {
        switch (keyword ?? "repeat").lowercased() {
        case "no-repeat": return .noRepeat
        case "space":     return .space
        case "round":     return .round
        default:          return .repeat // "repeat" + the CSS initial
        }
    }

    /// Place tiles of `tile` px along an axis of `area` px.
    ///
    /// `anchor` is the background-position-resolved offset of the FIRST
    /// tile (free-space × fraction + any px offset). Only NO_REPEAT
    /// honours it exactly; REPEAT phase-shifts the grid through it
    /// (css-backgrounds-3 §3.6), SPACE/ROUND pin the pattern to the
    /// area edges per §3.7.
    static func axisPlan(area: CGFloat, tile: CGFloat, anchor: CGFloat,
                         mode: AxisRepeat) -> AxisPlan {
        // Degenerate inputs: nothing sensible to draw. CSS treats
        // zero-size images as invisible rather than dividing by zero.
        if area <= 0 || tile <= 0 { return AxisPlan(tileSize: tile, origins: []) }
        switch mode {
        // Single tile at the position anchor.
        case .noRepeat:
            return AxisPlan(tileSize: tile, origins: [anchor])
        // Edge-to-edge tiling phase-shifted so one tile edge lands on
        // the anchor: normalize the anchor into (−tile, 0] so the walk
        // begins just left of the area and every tile is grid-aligned;
        // the last tile may clip past the right edge — spec'd (§3.6:
        // the pattern is infinite, clipped to the painting area).
        case .repeat:
            // Swift's truncatingRemainder matches Kotlin's `%` for the
            // sign convention this normalization relies on.
            var start = anchor.truncatingRemainder(dividingBy: tile)
            if start > 0 { start -= tile }
            var origins: [CGFloat] = []
            var x = start
            while x < area {
                origins.append(x)
                x += tile
            }
            return AxisPlan(tileSize: tile, origins: origins)
        // §3.7 space: as many WHOLE tiles as fit, first and last flush
        // with the area edges, leftover split into equal gaps BETWEEN
        // tiles. Fewer than two fitting → the single tile falls back to
        // the position anchor (spec: "background-position is ignored
        // unless only one image can be placed").
        case .space:
            let n = Int(floor(area / tile))
            if n >= 2 {
                let gap = (area - CGFloat(n) * tile) / CGFloat(n - 1)
                return AxisPlan(tileSize: tile,
                                origins: (0..<n).map { CGFloat($0) * (tile + gap) })
            }
            return AxisPlan(tileSize: tile, origins: [anchor])
        // §3.7 round: rescale the tile so a WHOLE number fills the area
        // exactly — X' = area / round(area / tile), never fewer than one
        // tile. `.rounded()` (to-nearest, ties away from zero) matches
        // Kotlin's roundToInt half-up for the positive inputs here.
        case .round:
            let n = max(Int((area / tile).rounded()), 1)
            let rounded = area / CGFloat(n)
            return AxisPlan(tileSize: rounded,
                            origins: (0..<n).map { CGFloat($0) * rounded })
        }
    }
}
