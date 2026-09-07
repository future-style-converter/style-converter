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
    // The four sides ride in `ClipInsetSides` (points + optional
    // reference-box fraction each — wave 46, lane Y4).
    case inset(sides: ClipInsetSides, cornerRadius: CGFloat,
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
    // `clip-path: border-box` etc. — a geometry-box keyword with no
    // shape. css-masking-1 §5.1: the clip region IS that reference box,
    // corner curves included (a `border-radius: 50px` border-box clips
    // to the circle; the margin box's corners follow css-shapes-1 §4).
    // Which box is in `ClipConfig.geometryBox`. Wave 46 (lane Y4):
    // before this the case was an identity no-op, so WPT clip-path-
    // marginBox-1b/1c/1d's 200px outline flooded the canvas.
    case geometryBoxOnly(box: String)
}

// css-shapes-1 §3.1 `inset()` sides. Each side is a `<length-percentage>`
// measured inward from the REFERENCE BOX edge: the points value plus an
// optional 0…1 fraction of the box's height (top/bottom) or width
// (left/right), resolved by InsetShape at draw time. Before wave 46 a
// percent side silently read as 0 pt (WPT clip-path-inset-round-percent:
// `inset(80% 0 0 round 8%)` rendered the whole box).
struct ClipInsetSides: Equatable {
    var top: CGFloat = 0
    var right: CGFloat = 0
    var bottom: CGFloat = 0
    var left: CGFloat = 0
    var topFraction: CGFloat? = nil
    var rightFraction: CGFloat? = nil
    var bottomFraction: CGFloat? = nil
    var leftFraction: CGFloat? = nil
}

// css-masking-1 §5.1 `<geometry-box>` — the reference box every basic
// shape resolves against (and the clip itself for the bare keyword
// form). The SVG-only fill-box / stroke-box / view-box keywords map per
// the spec's used-value rule for an element with a CSS layout box
// (fill → content, stroke / view → border) in the extractor.
enum ClipGeometryBox: Equatable {
    case marginBox, borderBox, paddingBox, contentBox
}

// The element's own box metrics, read from the same IR the layout
// chain consumes (ClipExtractor.boxMetrics), so the applier can derive
// the margin / padding / content box from the clip's rect — which on
// iOS is the BORDER box: `.engineClipPath` sits inside the margin
// modifier and outside padding/borders (StyleBuilder.applyGroupEffects).
struct ClipBoxMetrics: Equatable {
    // Declared margins in points (negative allowed; `auto` reads as 0).
    var marginTop: CGFloat = 0, marginRight: CGFloat = 0
    var marginBottom: CGFloat = 0, marginLeft: CGFloat = 0
    // USED border widths — 0 for a none/hidden-style side (CSS2 §8.5.3).
    var borderTop: CGFloat = 0, borderRight: CGFloat = 0
    var borderBottom: CGFloat = 0, borderLeft: CGFloat = 0
    // Resolved paddings in points.
    var paddingTop: CGFloat = 0, paddingRight: CGFloat = 0
    var paddingBottom: CGFloat = 0, paddingLeft: CGFloat = 0
    // The border box's corner curves (percent axes resolve at draw time).
    var radius: BorderRadiusConfig = BorderRadiusConfig()
    // No metrics: every reference box coincides with the clip rect.
    static let none = ClipBoxMetrics()
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
    // css-masking-1 §5.1 reference box — border-box unless the wire
    // named another (alone or next to a shape). Wave 46 (lane Y4):
    // before this the keyword was read and dropped ("documented TODO"),
    // so `circle(farthest-side) content-box` clipped to the 180px border
    // box instead of the 100px content box (WPT contentBox-1a).
    var geometryBox: ClipGeometryBox = .borderBox
    // The box metrics the reference box derives from (see the struct).
    var box: ClipBoxMetrics = .none
    // Winding rule (only consulted for .polygon/.path shapes).
    var rule: ClipRule = .nonzero
    // css-shapes-1 §3.1 `path(<fill-rule>?, <string>)` — the fill rule
    // carried INSIDE the path() function (the IR `rule` key), distinct
    // from the `clip-rule` property above which only applies to SVG
    // content. WPT clip-path-path-002 is `path(evenodd, …)` with no
    // clip-rule declared. nil = nonzero (the function's default).
    var pathFillRule: ClipRule? = nil
    // Legacy clip rectangle, applied via `.clipShape(Rectangle())`
    // after inset framing. Mutually exclusive with `shape` in practice.
    var legacy: LegacyClip? = nil
    // Touched flag — true the moment any extractor wrote here.
    var touched: Bool = false
}
