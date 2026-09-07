//
//  BorderImageMath.swift
//  StyleEngine/borders/image — BI-IOS lane (9-slice painter).
//
//  PURE geometry for CSS border-image (css-backgrounds-3 §5): width
//  resolution (§5.3), outset resolution (§5.4), the nine-region grid
//  shared by slice lines (§5.2) and the border image area (§5.3), and
//  the per-edge tile plan for the repeat keywords (§5.5). No SwiftUI
//  types, so XCTest (BorderImageMathTests) pins every number without
//  rendering — mirroring the Compose runtime's geometry in
//  borders/image/BorderImageApplier.kt so the natives share one set of
//  numbers (the §6.3 resolve() pins carry over test-for-test).
//

import Foundation
import CoreGraphics

/// Namespace enum (never instantiated) — matches BackgroundTileMath.
enum BorderImageMath {

    /// One CGFloat per physical side — resolved widths or outsets.
    struct Sides: Equatable {
        /// Clockwise from top, the CSS four-value order (§6.3 grammar).
        var top: CGFloat; var right: CGFloat; var bottom: CGFloat; var left: CGFloat
        /// True when NO side paints — the Compose Borders_C06 early-out:
        /// the initial `1` multiplier on a border-less element resolves
        /// every side to 0 and the border image must paint nothing.
        var isAllZero: Bool { top <= 0 && right <= 0 && bottom <= 0 && left <= 0 }
    }

    // MARK: - §6.3 border-image-width

    /// Resolve ONE border-image-width value per css-backgrounds-3 §5.3.
    /// `computedBorder` is the side's COMPUTED border-width (0 when the
    /// side has no border-style — §4.3), the basis for `<number>` values
    /// and the initial value `1`. `boxExtent` is the border image area's
    /// dimension on this side's axis (height for top/bottom, width for
    /// left/right) — the §6.3 percentage basis. Compose still
    /// approximates percentages against the computed border (its resolve
    /// runs before layout); iOS resolves at draw time where the area is
    /// known, so it takes the true basis — an intentional, spec-directed
    /// divergence (the Compose file carries the matching TODO).
    static func resolveWidth(_ dim: BorderImageDimension?, computedBorder: CGFloat,
                             slice: BorderImageSliceEdge?, boxExtent: CGFloat) -> CGFloat {
        switch dim {
        // Initial value is the NUMBER 1 → 1 × computed border-width.
        case nil: return computedBorder
        // auto → the corresponding slice's intrinsic size when it is an
        // absolute px count (§6.3); percentage slices refer to the source
        // image, unknown here, so fall back to the computed border width
        // (same policy as Compose's resolve()).
        case .auto:
            if let s = slice, !s.isPercent { return s.value }
            return computedBorder
        // <length> stays literal regardless of the border (§6.3).
        case .length(let px): return max(px, 0)
        // <percentage> of the border image area extent (§6.3). Clamped:
        // negative widths are invalid per the grammar.
        case .percent(let p): return max(boxExtent * p / 100, 0)
        // <number> = multiple of the computed border-width (§6.3).
        case .number(let n): return max(computedBorder * n, 0)
        }
    }

    /// All four §6.3 widths against a concrete border image area `box`
    /// (top/bottom percentages use the height, left/right the width).
    static func resolvedWidths(_ cfg: BorderImageConfig, box: CGSize) -> Sides {
        Sides(top: resolveWidth(cfg.widthTop, computedBorder: cfg.computedBorderTop,
                                slice: cfg.sliceTop, boxExtent: box.height),
              right: resolveWidth(cfg.widthRight, computedBorder: cfg.computedBorderRight,
                                  slice: cfg.sliceRight, boxExtent: box.width),
              bottom: resolveWidth(cfg.widthBottom, computedBorder: cfg.computedBorderBottom,
                                   slice: cfg.sliceBottom, boxExtent: box.height),
              left: resolveWidth(cfg.widthLeft, computedBorder: cfg.computedBorderLeft,
                                 slice: cfg.sliceLeft, boxExtent: box.width))
    }

    /// Box-size-free "would anything paint?" gate for the applier's
    /// early-out (the geometry is unknown at modifier time). A positive
    /// PROBE extent is exact: resolveWidth is linear in `boxExtent` for
    /// percentages and constant for every other shape, so all-zero at
    /// probe 100 ⟺ all-zero at every box size. Mirrors Compose's
    /// zero-resolved-width identity path (Borders_C06, SSIM 0.5861 when
    /// the old 8dp fallback painted a phantom frame).
    static func canPaint(_ cfg: BorderImageConfig) -> Bool {
        !resolvedWidths(cfg, box: CGSize(width: 100, height: 100)).isAllZero
    }

    // MARK: - §6.4 border-image-outset

    /// Resolve one border-image-outset value per css-backgrounds-3 §5.4:
    /// lengths are literal, numbers multiply the computed border-width,
    /// initial value 0. `auto`/percent are not in the outset grammar —
    /// they resolve to 0 (mirrors Compose's resolveOutset()).
    static func resolveOutset(_ dim: BorderImageDimension?, computedBorder: CGFloat) -> CGFloat {
        switch dim {
        // Initial value 0 — no expansion.
        case nil: return 0
        // Not a valid outset value (§6.4 grammar excludes auto).
        case .auto: return 0
        // <length> literal.
        case .length(let px): return px
        // Not valid for outset either — inert like Compose.
        case .percent: return 0
        // <number> × computed border-width.
        case .number(let n): return computedBorder * n
        }
    }

    /// All four outsets (box-independent, so resolvable at modifier time).
    static func resolvedOutsets(_ cfg: BorderImageConfig) -> Sides {
        Sides(top: resolveOutset(cfg.outsetTop, computedBorder: cfg.computedBorderTop),
              right: resolveOutset(cfg.outsetRight, computedBorder: cfg.computedBorderRight),
              bottom: resolveOutset(cfg.outsetBottom, computedBorder: cfg.computedBorderBottom),
              left: resolveOutset(cfg.outsetLeft, computedBorder: cfg.computedBorderLeft))
    }

    // MARK: - §6.1 slice lines

    /// One slice offset in IMAGE PIXELS: `<number>` is px, `<percentage>`
    /// is relative to the image's extent on that axis (§6.1). A missing
    /// edge maps to 0 like Compose's toPixels() — NOTE the spec initial
    /// is 100%, but the converter's slice parser always emits all four
    /// edges when the property is present, so absent-edge only means
    /// "property never parsed", where painting nothing matches Compose.
    static func slicePx(_ edge: BorderImageSliceEdge?, extent: CGFloat) -> CGFloat {
        guard let e = edge else { return 0 }
        // Negative slices are invalid per the grammar — clamp to 0.
        return max(e.isPercent ? e.value / 100 * extent : e.value, 0)
    }

    // MARK: - The nine-region grid (§6.1 source / §6.2 destination)

    /// The 9-slice regions of a rect, named like the Compose src/dst
    /// rect pairs (corners fixed, edges tiled, center = fill).
    struct Grid: Equatable {
        var topLeft: CGRect; var topCenter: CGRect; var topRight: CGRect
        var middleLeft: CGRect; var center: CGRect; var middleRight: CGRect
        var bottomLeft: CGRect; var bottomCenter: CGRect; var bottomRight: CGRect
    }

    /// Cut `bounds` into the nine border-image regions with band sizes
    /// top/right/bottom/left. Used twice per paint: on the image rect
    /// with slice px (§6.1) and on the border image area with resolved
    /// widths (§6.2). When opposing bands would overlap, both scale by
    /// size/(sum) — §6.1's "proportionally reduced" rule for slices and
    /// §6.2's identical rule for widths (Compose skips this and emits
    /// inverted rects; the reduction is the spec'd fix).
    static func nineGrid(bounds: CGRect, top: CGFloat, right: CGFloat,
                         bottom: CGFloat, left: CGFloat) -> Grid {
        // Negative bands are invalid input — clamp before the overlap fix.
        var t = max(top, 0), r = max(right, 0), b = max(bottom, 0), l = max(left, 0)
        // Horizontal overlap: left+right may not exceed the width (§6.1/§6.2).
        if l + r > bounds.width, l + r > 0 { let f = bounds.width / (l + r); l *= f; r *= f }
        // Vertical overlap: top+bottom may not exceed the height.
        if t + b > bounds.height, t + b > 0 { let f = bounds.height / (t + b); t *= f; b *= f }
        // Center extents — non-negative by construction after the fix.
        let cw = bounds.width - l - r
        let ch = bounds.height - t - b
        // Column x-origins (left band, center, right band) and row y's.
        let x0 = bounds.minX, x1 = x0 + l, x2 = bounds.maxX - r
        let y0 = bounds.minY, y1 = y0 + t, y2 = bounds.maxY - b
        // Assemble the nine rects row by row (top / middle / bottom).
        return Grid(topLeft: CGRect(x: x0, y: y0, width: l, height: t),
                    topCenter: CGRect(x: x1, y: y0, width: cw, height: t),
                    topRight: CGRect(x: x2, y: y0, width: r, height: t),
                    middleLeft: CGRect(x: x0, y: y1, width: l, height: ch),
                    center: CGRect(x: x1, y: y1, width: cw, height: ch),
                    middleRight: CGRect(x: x2, y: y1, width: r, height: ch),
                    bottomLeft: CGRect(x: x0, y: y2, width: l, height: b),
                    bottomCenter: CGRect(x: x1, y: y2, width: cw, height: b),
                    bottomRight: CGRect(x: x2, y: y2, width: r, height: b))
    }

    // MARK: - §6.2 edge tile plan

    /// Along-axis tiling of one edge band: `dest` is the band's length,
    /// `src` the source slice's length in image px (the tile pitch basis,
    /// mirroring Compose's unscaled-source-px tiles). Delegates the
    /// round/space arithmetic to BackgroundTileMath.axisPlan — the same
    /// §3.7-derived gap/rescale math, anchored at 0 because border-image
    /// edges have no background-position (§6.2 pins the pattern to the
    /// band). Offsets are relative to the band's leading edge.
    static func edgePlan(dest: CGFloat, src: CGFloat,
                         mode: BorderImageRepeat) -> BackgroundTileMath.AxisPlan {
        // Degenerate band or empty slice — nothing to tile (the caller
        // skips the draw; avoids the ÷0 inside the plans below).
        guard dest > 0, src > 0 else {
            return BackgroundTileMath.AxisPlan(tileSize: src, origins: [], ends: [])
        }
        switch mode {
        // stretch (the initial value): one tile scaled to the full band.
        case .stretch:
            return BackgroundTileMath.AxisPlan(tileSize: dest, origins: [0], ends: [dest])
        // repeat: whole source-pixel tiles from the band start; the last
        // tile overflows and the painter clips it to the band (§6.2 says
        // tiles are clipped — Compose squashes the last tile instead;
        // clipping is the spec'd geometry and what Chromium paints).
        case .repeatTile:
            return BackgroundTileMath.axisPlan(area: dest, tile: src, anchor: 0, mode: .repeat)
        // round: rescale so round(dest/src) whole tiles fit exactly —
        // the identical count formula Compose uses (max(1, round(d/s))).
        case .round:
            return BackgroundTileMath.axisPlan(area: dest, tile: src, anchor: 0, mode: .round)
        // space: whole tiles flush with both band ends + equal gaps
        // between. Fewer than two whole tiles → Compose stretches the
        // single tile across the band (its count==1 branch); mirrored
        // here so the natives paint the same degenerate case.
        case .space:
            if floor(dest / src) >= 2 {
                return BackgroundTileMath.axisPlan(area: dest, tile: src, anchor: 0, mode: .space)
            }
            return BackgroundTileMath.AxisPlan(tileSize: dest, origins: [0], ends: [dest])
        }
    }
}
