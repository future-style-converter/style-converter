//
//  ClipShapes.swift
//  StyleEngine/effects/clip — the SwiftUI `Shape` implementations behind
//  ClipApplier, split out in wave 46 (lane Y4) to keep the applier under
//  the house size target. Every shape resolves against the css-masking-1
//  §7.1 reference box through `ClipRef.frame(in:)` at draw time; with no
//  `<geometry-box>` and no metrics that box IS the border-box rect
//  `.clipShape` hands in — the pre-wave-46 arithmetic, byte for byte.
//  `fileprivate`→`internal` visibility is the only change from the
//  in-applier originals.
//

import SwiftUI

// Bare `<geometry-box>` — the reference box's rounded rectangle.
struct ReferenceBoxClip: Shape {
    let ref: ClipRef
    func path(in rect: CGRect) -> Path {
        ClipReferenceBox.roundedPath(ref.frame(in: rect))
    }
}

// Inset — shrinks the reference box by the four CSS sides and optionally
// rounds the corners uniformly.
struct InsetShape: Shape {
    let sides: ClipInsetSides
    let cornerRadius: CGFloat
    // CSS percentage `round` value — resolved against the rect at draw
    // time. nil when the source was a plain length.
    let cornerRadiusFraction: CGFloat?
    let ref: ClipRef
    func path(in rect: CGRect) -> Path {
        let box = ref.frame(in: rect).rect
        // css-shapes-1 §3.1: a percent side is that fraction of the
        // reference box's height (top/bottom) or width (left/right).
        let t = sides.top + (sides.topFraction.map { box.height * $0 } ?? 0)
        let rr = sides.right + (sides.rightFraction.map { box.width * $0 } ?? 0)
        let b = sides.bottom + (sides.bottomFraction.map { box.height * $0 } ?? 0)
        let l = sides.left + (sides.leftFraction.map { box.width * $0 } ?? 0)
        // Build the inset rect by chopping each side off the box.
        let r = CGRect(x: box.minX + l,
                       y: box.minY + t,
                       width: max(0, box.width - l - rr),
                       height: max(0, box.height - t - b))
        // Per CSS Backgrounds 3 §5.2 a percent radius produces an
        // ellipse with rx = width × pct, ry = height × pct. SwiftUI's
        // built-in `Path(roundedRect:cornerSize:)` clamps cornerSize so
        // a non-square box can't render true elliptical corners (rx≠ry)
        // — `inset(0 round 50%)` collapses to the smallest-axis radius
        // and shows rectangular sides. Build the path by hand instead:
        // four 90° arcs scaled per-axis via CGAffineTransform stitch into
        // a true ellipse / pill / stadium shape that matches web's
        // rendering pixel-for-pixel.
        // The percent basis is the REFERENCE box (Blink's
        // BasicShapeInset::GetPath sizes the radii from the bounding box,
        // then constrains them against the inset rect) — WPT
        // clip-path-inset-round-percent: `inset(80% 0 0 round 8%)` on a
        // 100px box is an 8px radius on the 100×20 strip, not 8×1.6. The
        // css-backgrounds-3 §4.5 overlap rule then scales BOTH axes by
        // one factor so the corners stay similar ellipses.
        if let frac = cornerRadiusFraction {
            let rx0 = cornerRadius + box.width * frac
            let ry0 = cornerRadius + box.height * frac
            let f = min(1, min(rx0 > 0 ? r.width / (2 * rx0) : 1,
                               ry0 > 0 ? r.height / (2 * ry0) : 1))
            return ellipticalRoundedRectPath(in: r, rx: rx0 * f, ry: ry0 * f)
        }
        return Path(roundedRect: r, cornerRadius: cornerRadius)
    }
}

// Build a rounded rectangle whose four corners are quarter-ellipses with
// independent radii rx (horizontal) and ry (vertical). When rx == ry it
// degenerates to a normal rounded rectangle; when rx == width/2 and
// ry == height/2 it becomes a perfect ellipse — matching CSS's
// `border-radius: 50% / 50%` / `inset(0 round 50%)` behaviour.
func ellipticalRoundedRectPath(in r: CGRect, rx: CGFloat, ry: CGFloat) -> Path {
    var p = Path()
    let l = r.minX, t = r.minY, rt = r.maxX, b = r.maxY
    // Start at top-left corner, just past the rounded portion.
    p.move(to: CGPoint(x: l + rx, y: t))
    // Top edge → top-right corner
    p.addLine(to: CGPoint(x: rt - rx, y: t))
    p.addQuadCurve(to: CGPoint(x: rt, y: t + ry),
                   control: CGPoint(x: rt, y: t))
    // Right edge → bottom-right corner
    p.addLine(to: CGPoint(x: rt, y: b - ry))
    p.addQuadCurve(to: CGPoint(x: rt - rx, y: b),
                   control: CGPoint(x: rt, y: b))
    // Bottom edge → bottom-left corner
    p.addLine(to: CGPoint(x: l + rx, y: b))
    p.addQuadCurve(to: CGPoint(x: l, y: b - ry),
                   control: CGPoint(x: l, y: b))
    // Left edge → close at top-left corner
    p.addLine(to: CGPoint(x: l, y: t + ry))
    p.addQuadCurve(to: CGPoint(x: l + rx, y: t),
                   control: CGPoint(x: l, y: t))
    p.closeSubpath()
    return p
}

// Circle clip — radius resolution dispatched by ShapeRadiusKind:
//   .length        → use `radius` verbatim (already in points)
//   .percent       → `radius` is a 0…1 fraction of min(width, height)
//   .closestSide   → distance from centre to nearest box edge
//   .farthestSide  → distance from centre to furthest box edge
// The two keyword cases were added in swarm-003 clip-path-borderBox-1a;
// before that, `circle(farthest-side)` lost the keyword at parse and
// silently rendered with a 0-radius fallback. All of it in
// REFERENCE-BOX space (wave 46): `rect` below is the chosen box.
struct CircleClip: Shape {
    let radius: CGFloat
    let kind: ShapeRadiusKind
    let cx, cy: CGFloat    // unit-space centre.
    let ref: ClipRef
    func path(in outer: CGRect) -> Path {
        let rect = ref.frame(in: outer).rect
        let centre = CGPoint(x: rect.minX + rect.width * cx,
                             y: rect.minY + rect.height * cy)
        let effR: CGFloat = {
            switch kind {
            case .length:  return radius
            case .percent: return min(rect.width, rect.height) * radius
            case .closestSide:
                // CSS Shapes 1: distance from centre to nearest side.
                return min(min(centre.x - rect.minX, rect.maxX - centre.x),
                           min(centre.y - rect.minY, rect.maxY - centre.y))
            case .farthestSide:
                // Distance from centre to furthest side.
                return max(max(centre.x - rect.minX, rect.maxX - centre.x),
                           max(centre.y - rect.minY, rect.maxY - centre.y))
            }
        }()
        var p = Path()
        p.addEllipse(in: CGRect(x: centre.x - effR, y: centre.y - effR,
                                width: effR * 2, height: effR * 2))
        return p
    }
}

// Ellipse clip — per-axis radius resolution. `closest-side` and
// `farthest-side` resolve axis-locally per CSS Shapes 1 §3.1 (horizontal
// distances for rx, vertical for ry). Reference-box space as above.
struct EllipseClip: Shape {
    let rx, ry: CGFloat
    let rxKind, ryKind: ShapeRadiusKind
    let cx, cy: CGFloat
    let ref: ClipRef
    func path(in outer: CGRect) -> Path {
        let rect = ref.frame(in: outer).rect
        let centre = CGPoint(x: rect.minX + rect.width * cx,
                             y: rect.minY + rect.height * cy)
        func resolve(_ r: CGFloat, _ k: ShapeRadiusKind, isHorizontal: Bool) -> CGFloat {
            switch k {
            case .length:  return r
            case .percent: return (isHorizontal ? rect.width : rect.height) * r
            case .closestSide:
                return isHorizontal
                    ? min(centre.x - rect.minX, rect.maxX - centre.x)
                    : min(centre.y - rect.minY, rect.maxY - centre.y)
            case .farthestSide:
                return isHorizontal
                    ? max(centre.x - rect.minX, rect.maxX - centre.x)
                    : max(centre.y - rect.minY, rect.maxY - centre.y)
            }
        }
        let rrx = resolve(rx, rxKind, isHorizontal: true)
        let rry = resolve(ry, ryKind, isHorizontal: false)
        var p = Path()
        p.addEllipse(in: CGRect(x: centre.x - rrx, y: centre.y - rry,
                                width: rrx * 2, height: rry * 2))
        return p
    }
}

// Polygon clip — each vertex's x/y axis is independently a percentage of
// the reference box or an absolute length from its top-left (css-shapes-1
// §3.1; WPT clip-path-geometryBox-2 pins the margin-box case). The fill
// rule is applied by the caller through `.clipShape(_:style:)`.
struct PolygonClip: Shape {
    let points: [PolygonPoint]
    let ref: ClipRef
    func path(in outer: CGRect) -> Path {
        let rect = ref.frame(in: outer).rect
        // Resolve one axis value to a concrete coordinate inside `rect`.
        // `extent` is the relevant dimension (width for x, height for y).
        // A `.percent(50)` axis on a 100-wide rect resolves to 50; a
        // `.length(100)` axis resolves to 100 regardless of extent.
        func resolve(_ axis: PolygonAxis, extent: CGFloat) -> CGFloat {
            switch axis {
            case .percent(let v): return extent * v / 100
            case .length(let v):  return v
            }
        }
        var p = Path()
        guard !points.isEmpty else { return p }
        for (i, pt) in points.enumerated() {
            let x = rect.minX + resolve(pt.x, extent: rect.width)
            let y = rect.minY + resolve(pt.y, extent: rect.height)
            if i == 0 { p.move(to: CGPoint(x: x, y: y)) }
            else { p.addLine(to: CGPoint(x: x, y: y)) }
        }
        p.closeSubpath()
        return p
    }
}

// SVG path clip — css-shapes-1 §3.1 `path()`: the data string is parsed
// by SvgPathParser (full SVG 1.1 §8.3 grammar, wave 46) and placed with
// the reference box's top-left as its user-space origin. An unparsable
// string clips NOTHING away (an empty Path would hide the whole element
// — the wave-45 iOS rendering of clip-path-path-001/002). Fill rule via
// the caller's FillStyle.
struct SvgPathClip: Shape {
    let data: String
    let ref: ClipRef
    func path(in outer: CGRect) -> Path {
        let rect = ref.frame(in: outer).rect
        guard let parsed = SvgPathParser.parse(data) else { return Path(outer) }
        return parsed.offsetBy(dx: rect.minX, dy: rect.minY)
    }
}

// CSS `rect(t r b l)` clip-path form — top-left corner at `left, top`,
// bottom-right at `right, bottom`, all from the reference box's top-left.
// "auto" components fall back to the box's own edge.
struct RectClip: Shape {
    let top, right, bottom, left: CGFloat?
    let cornerRadius: CGFloat
    let ref: ClipRef
    func path(in outer: CGRect) -> Path {
        let rect = ref.frame(in: outer).rect
        let t = top ?? 0
        let r = right ?? rect.width
        let b = bottom ?? rect.height
        let l = left ?? 0
        let box = CGRect(x: rect.minX + l, y: rect.minY + t,
                         width: max(0, r - l), height: max(0, b - t))
        return Path(roundedRect: box, cornerRadius: cornerRadius)
    }
}

// CSS `xywh(x y w h)` — explicit top-left corner + dimensions, from the
// reference box's top-left.
struct XywhClip: Shape {
    let x, y, w, h, cornerRadius: CGFloat
    let ref: ClipRef
    func path(in outer: CGRect) -> Path {
        let rect = ref.frame(in: outer).rect
        let box = CGRect(x: rect.minX + x, y: rect.minY + y,
                         width: w, height: h)
        return Path(roundedRect: box, cornerRadius: cornerRadius)
    }
}

// Legacy `clip: rect(t, r, b, l)` — identical geometry to modern rect(),
// measured from the border box (CSS 2.1 §11.1.2), which IS the rect here.
struct LegacyClipRect: Shape {
    // nil = CSS `auto`. Resolved against `rect` here at draw time:
    // top/left auto → 0; bottom/right auto → element's full extent.
    let top, right, bottom, left: CGFloat?
    func path(in rect: CGRect) -> Path {
        let t = top ?? 0
        let l = left ?? 0
        let r = right ?? rect.width
        let b = bottom ?? rect.height
        let box = CGRect(x: rect.minX + l, y: rect.minY + t,
                         width: max(0, r - l),
                         height: max(0, b - t))
        return Path(box)
    }
}
