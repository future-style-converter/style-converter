//
//  ClipConfig.swift
//  StyleEngine/effects/clip — Phase 8.
//
//  CSS `clip-path` plus the legacy `clip` rectangle. Both map onto
//  SwiftUI `.clipShape(_:)` at render time, so we normalise every
//  variant to a concrete `Shape` description that `ClipApplier` can
//  feed through `.clipShape`. `ClipRule` is a nonzero / evenodd
//  enum that only matters for polygon winding on iOS 16+.
//

import SwiftUI

// A single clip-path shape, canonicalised so the applier can
// build a SwiftUI Shape without re-parsing IR.
enum ClipShape: Equatable {
    // `clip-path: none` — no clipping applied.
    case none
    // `inset(top right bottom left)` with optional uniform corner
    // radius. `cornerRadiusFraction` carries CSS `round <pct>` values
    // that resolve against the box's own dimensions at draw time —
    // can't be flattened into `cornerRadius` (a CGFloat in points)
    // without knowing the rect, so the applier picks one of the two.
    case inset(top: CGFloat, right: CGFloat, bottom: CGFloat,
               left: CGFloat, cornerRadius: CGFloat,
               cornerRadiusFraction: CGFloat?)
    // `circle(radius at cx cy)` — radius is either a length / percent OR
    // a `<shape-radius>` keyword (`closest-side` / `farthest-side` per
    // CSS Shapes 1 §3.1). `radiusKind` discriminates the three flavours
    // so the applier (CircleClip) can pick the right resolution path at
    // draw time. Center is in unit-space (0…1). Defaults to 50% 50%
    // center when `at` was omitted.
    case circle(radius: CGFloat, radiusKind: ShapeRadiusKind,
                cx: CGFloat, cy: CGFloat)
    // `ellipse(rx ry at cx cy)` — same centre convention as circle.
    // Each axis carries its own ShapeRadiusKind so e.g.
    // `ellipse(closest-side farthest-side)` lands cleanly.
    case ellipse(rx: CGFloat, ry: CGFloat,
                 rxKind: ShapeRadiusKind, ryKind: ShapeRadiusKind,
                 cx: CGFloat, cy: CGFloat)
    // `polygon((x,y), (x,y), …)` — each vertex is a CSS Shapes 2 §3.1.4
    // `<length-percentage>` pair. Per-axis the value is either a
    // percentage of the box (legacy form, raw JSON number) or an absolute
    // CSS length in points (new IRLength wire form `{"px": N}`).
    // PolygonAxis.kind distinguishes them so the applier resolves each
    // axis independently at draw time.
    case polygon(points: [PolygonPoint])
    // `path('M 10 10 L 20 20 Z')` — raw SVG path data.
    case path(data: String)
    // `rect(top right bottom left)` / `xywh(x y w h)` — reuse inset with
    // explicit top-left coords.
    case rect(top: CGFloat?, right: CGFloat?, bottom: CGFloat?, left: CGFloat?,
             cornerRadius: CGFloat)
    case xywh(x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, cornerRadius: CGFloat)
    // `clip-path: url(#id)` — references an SVG clipPath. No SwiftUI
    // equivalent — we carry the id so the applier can log / skip.
    case url(id: String)
    // `clip-path: border-box` etc. — just a geometry-box keyword with no
    // shape. We treat these as identity for now (no clipping), matching
    // CSS behaviour when no shape is specified.
    case geometryBoxOnly(box: String)
}

// CSS Shapes 1 §3.1 `<shape-radius>` family. A single axis of a circle
// or ellipse can be one of three distinct things at parse time:
//   - an absolute length in points (the IRLength `{"px": N}` form)
//   - a percentage of the box (the IRLength `{"original":{v,u:"PERCENT"}}` form)
//   - the keyword `closest-side` or `farthest-side` (the new variant
//     unblocked by swarm-003 clip-path-borderBox-1a)
// The applier resolves each kind against the rendered rect.
enum ShapeRadiusKind: Equatable {
    case length        // CGFloat already in points
    case percent       // CGFloat as a 0…1 fraction (50% → 0.5)
    case closestSide   // distance from centre to nearest box edge
    case farthestSide  // distance from centre to furthest box edge
}

// Winding rule for polygon clipping. SwiftUI's Path ignores this by
// default (always nonzero) so we document the limitation and only use
// evenodd for the path-shape case where we control the Path directly.
enum ClipRule: Equatable {
    case nonzero
    case evenodd
}

// One polygon vertex. CSS Shapes 2 §3.1.4 defines polygon points as
// `<length-percentage>` pairs — each axis can independently be either
// a percentage of the box (legacy / common form) or an absolute length
// in points (the new form that unblocks WPT fixtures with `px` vertices
// like clip-path-blending-offset).
struct PolygonPoint: Equatable {
    let x: PolygonAxis
    let y: PolygonAxis
}

enum PolygonAxis: Equatable {
    // Percentage of the box dimension on the matching axis (0…100).
    case percent(CGFloat)
    // Absolute length in CGFloat points (resolved px from IRLength).
    case length(CGFloat)
}

// Legacy CSS 2.1 `clip: rect(t, r, b, l)`. Only effective for
// `position: absolute` elements per spec — we apply universally since
// our gallery items aren't positioned.
enum LegacyClip: Equatable {
    case auto
    // Each side optional — `nil` means CSS `auto` for that edge: 0 on
    // top/left, the element's full extent on bottom/right (CSS 2.1
    // §11.1.2). The applier resolves at draw time when it knows
    // `rect.size`.
    case rect(top: CGFloat?, right: CGFloat?, bottom: CGFloat?, left: CGFloat?)
}

struct ClipConfig: Equatable {
    // Primary clip-path value. `nil` ≡ not set; `.some(.none)` ≡
    // explicit `clip-path: none`.
    var shape: ClipShape? = nil
    // Winding rule (only consulted for .polygon/.path shapes).
    var rule: ClipRule = .nonzero
    // Legacy clip rectangle, applied via `.clipShape(Rectangle())`
    // after inset framing. Mutually exclusive with `shape` in practice.
    var legacy: LegacyClip? = nil
    // Touched flag — true the moment any extractor wrote here.
    var touched: Bool = false
}
