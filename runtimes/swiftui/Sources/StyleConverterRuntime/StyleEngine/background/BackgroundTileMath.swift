//
//  BackgroundTileMath.swift
//  StyleEngine/background — gradient-geometry lane (space/round repeat).
//
//  css-backgrounds-3 §2.4 background-repeat tile placement — PURE math,
//  no SwiftUI types, ported 1:1 from the Compose runtime's
//  BackgroundTileMath.kt so both native platforms pin the same numbers
//  (XCTest mirror: BackgroundTileMathTests ↔ BackgroundTileMathTest.kt).
//  Callers build one AxisPlan per axis and draw the cartesian product of
//  the two per-axis [start, end) segment lists — pixel-snapped for the
//  abutting modes, see AxisPlan (BackgroundImageGeometry.tileRects).
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
    /// tile size plus every tile's DRAWN segment — tile i spans
    /// [origins[i], ends[i]). Empty `origins` = nothing to draw
    /// (degenerate tile/area).
    struct AxisPlan: Equatable {
        /// SHADER pitch along this axis — differs from the input only for
        /// `round`, which refits it to an integer count. Fractional (e.g.
        /// 200/7): gradient geometry must resolve against it (css-images-4
        /// §3.4.1 sizes the gradient box, never the rasterized rect).
        var tileSize: CGFloat
        /// Start offset of every tile along this axis. Pixel-snapped for
        /// the abutting modes (repeat/round) — see `ends`.
        var origins: [CGFloat]
        /// Closing edge of every tile (parallel to `origins`). For the
        /// abutting modes the edges are pixel-snapped so adjacent rects
        /// share INTEGER boundaries: two independently antialiased rects
        /// meeting on a fractional edge never sum to full coverage, so
        /// every interior boundary leaked a light background seam (the
        /// Repeat_Round 0.946 SSIM straggler vs Chromium's seamless
        /// pattern rasterization). Non-abutting modes (noRepeat/space)
        /// keep exact ends — their tiles never share an edge, so there is
        /// no seam to close and no reason to perturb the spec'd geometry.
        var ends: [CGFloat]
    }

    /// Pixel-snap one lattice edge. floor(x + 0.5) is Kotlin's roundToInt
    /// contract (ties toward +∞) spelled out so this port pins the
    /// IDENTICAL rule as BackgroundTileMath.kt — Swift's default
    /// `.rounded()` breaks ties away from zero, which diverges on
    /// negative REPEAT overhang edges like −20.5.
    private static func snap(_ x: CGFloat) -> CGFloat { floor(x + 0.5) }

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
    /// (css-backgrounds-3 §2.6), SPACE/ROUND pin the pattern to the
    /// area edges per §3.7.
    static func axisPlan(area: CGFloat, tile: CGFloat, anchor: CGFloat,
                         mode: AxisRepeat) -> AxisPlan {
        // Degenerate inputs: nothing sensible to draw. CSS treats
        // zero-size images as invisible rather than dividing by zero.
        if area <= 0 || tile <= 0 { return AxisPlan(tileSize: tile, origins: [], ends: []) }
        switch mode {
        // Single tile at the position anchor — non-abutting, so the end
        // stays the EXACT anchor + tile (no seam to close, see AxisPlan).
        case .noRepeat:
            return AxisPlan(tileSize: tile, origins: [anchor], ends: [anchor + tile])
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
            // Collect the FRACTIONAL grid edges (start + k·tile) — one
            // past the last tile so every tile has a closing edge.
            var edges: [CGFloat] = []
            var x = start
            while x < area {
                edges.append(x)
                x += tile
            }
            edges.append(x) // closing edge of the final (clipped) tile
            // REPEAT is an abutting lattice: snap every edge so adjacent
            // rects meet on integer pixels (identity for integer pitch +
            // anchor — see AxisPlan doc for the seam rationale).
            let snapped = edges.map(snap)
            // tileSize stays the fractional pitch for the shader; the
            // drawn segments are consecutive snapped edge pairs.
            return AxisPlan(tileSize: tile,
                            origins: Array(snapped.dropLast()),
                            ends: Array(snapped.dropFirst()))
        // §3.7 space: as many WHOLE tiles as fit, first and last flush
        // with the area edges, leftover split into equal gaps BETWEEN
        // tiles. Fewer than two fitting → the single tile falls back to
        // the position anchor (spec: "background-position is ignored
        // unless only one image can be placed"). Non-abutting (gaps
        // separate tiles), so ends stay the EXACT start + tile.
        case .space:
            let n = Int(floor(area / tile))
            if n >= 2 {
                let gap = (area - CGFloat(n) * tile) / CGFloat(n - 1)
                let starts = (0..<n).map { CGFloat($0) * (tile + gap) }
                return AxisPlan(tileSize: tile, origins: starts,
                                ends: starts.map { $0 + tile })
            }
            return AxisPlan(tileSize: tile, origins: [anchor], ends: [anchor + tile])
        // §3.7 round: rescale the tile so a WHOLE number fills the area
        // exactly — X' = area / round(area / tile), never fewer than one
        // tile. `.rounded()` (to-nearest, ties away from zero) matches
        // Kotlin's roundToInt half-up for the positive inputs here.
        case .round:
            let n = max(Int((area / tile).rounded()), 1)
            let rounded = area / CGFloat(n)
            // Fractional pitch (e.g. 200/7 ≈ 28.571) at snapped shared
            // edges: edge i = snap(i·X'), so tile i draws
            // [snap(i·X'), snap((i+1)·X')) — per-tile width varies ±1px
            // but adjacent rects abut on integer pixels (no AA seam).
            // The SHADER keeps the fractional `rounded` pitch untouched.
            let edges = (0...n).map { snap(CGFloat($0) * rounded) }
            return AxisPlan(tileSize: rounded,
                            origins: Array(edges.dropLast()),
                            ends: Array(edges.dropFirst()))
        }
    }
}
