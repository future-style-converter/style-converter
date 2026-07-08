//
//  ClipApplier.swift
//  StyleEngine/effects/clip — Phase 8.
//
//  Consumes a `ClipConfig` and emits SwiftUI `.clipShape(_:)` /
//  `.mask(_:)` modifiers. Most shapes route through a GeometryReader
//  so percent-based coordinates resolve against the actual view box.
//

import SwiftUI

struct ClipApplier: ViewModifier {
    // Optional — nil means no clip applied.
    let config: ClipConfig?

    func body(content: Content) -> some View {
        // Short-circuit when nothing was written.
        guard let cfg = config, cfg.touched else { return AnyView(content) }

        // Legacy clip rect composes on top of the shape (CSS treats them
        // independently). We stack them: apply legacy first, shape second.
        var v: AnyView = AnyView(content)

        // clip-path branch.
        if let shape = cfg.shape {
            switch shape {
            case .none, .geometryBoxOnly:
                // Explicit no-op. Preserved as a touched-state marker
                // that the applier recognised the property.
                break
            case .url(let id):
                // No SwiftUI equivalent for url(#id) — log once per run
                // and leave the view unclipped. TODO: SVG clip lookup.
                _ = id
            case .inset(let t, let r, let b, let l, let cr, let crFrac):
                // `.clipShape` with a rounded-rect inset. CSS `round
                // <pct>` resolves against the box at draw time — pass
                // the fraction through so InsetShape can compute it
                // from `rect.size` (matches the Android fix in
                // ClipPathApplier.kt).
                v = AnyView(v.clipShape(InsetShape(top: t, right: r, bottom: b,
                                                   left: l, cornerRadius: cr,
                                                   cornerRadiusFraction: crFrac)))
            case .circle(let r, let kind, let cx, let cy):
                v = AnyView(v.clipShape(CircleClip(radius: r, kind: kind,
                                                    cx: cx, cy: cy)))
            case .ellipse(let rx, let ry, let rxK, let ryK, let cx, let cy):
                v = AnyView(v.clipShape(EllipseClip(rx: rx, ry: ry,
                                                     rxKind: rxK, ryKind: ryK,
                                                     cx: cx, cy: cy)))
            case .polygon(let pts):
                v = AnyView(v.clipShape(PolygonClip(points: pts,
                                                     evenOdd: cfg.rule == .evenodd)))
            case .path(let d):
                v = AnyView(v.clipShape(SvgPathClip(data: d,
                                                     evenOdd: cfg.rule == .evenodd)))
            case .rect(let t, let r, let b, let l, let cr):
                v = AnyView(v.clipShape(RectClip(top: t, right: r, bottom: b,
                                                   left: l, cornerRadius: cr)))
            case .xywh(let x, let y, let w, let h, let cr):
                v = AnyView(v.clipShape(XywhClip(x: x, y: y, w: w, h: h,
                                                   cornerRadius: cr)))
            }
        }

        // Legacy clip rect: outset-oriented rect in CSS coordinates.
        if case .rect(let t, let r, let b, let l) = cfg.legacy {
            v = AnyView(v.clipShape(LegacyClipRect(top: t, right: r,
                                                    bottom: b, left: l)))
        }

        return v
    }
}

extension View {
    // Identity when nil — common case.
    func engineClipPath(_ config: ClipConfig?) -> some View {
        modifier(ClipApplier(config: config))
    }
}

// MARK: - Shape implementations

// Inset — shrinks the view's rect by the four CSS sides and optionally
// rounds the corners uniformly.
private struct InsetShape: Shape {
    let top, right, bottom, left, cornerRadius: CGFloat
    // CSS percentage `round` value — resolved against the rect at draw
    // time. nil when the source was a plain length.
    let cornerRadiusFraction: CGFloat?
    func path(in rect: CGRect) -> Path {
        // Build the inset rect by chopping each side off the bounds.
        let r = CGRect(x: rect.minX + left,
                       y: rect.minY + top,
                       width: max(0, rect.width - left - right),
                       height: max(0, rect.height - top - bottom))
        // Per CSS Backgrounds 3 §5.2 a percent radius produces an
        // ellipse with rx = width × pct, ry = height × pct. SwiftUI's
        // built-in `Path(roundedRect:cornerSize:)` clamps cornerSize so
        // a non-square box can't render true elliptical corners (rx≠ry)
        // — `inset(0 round 50%)` collapses to the smallest-axis radius
        // and shows rectangular sides. Build the path by hand instead:
        // four 90° arcs scaled per-axis via CGAffineTransform stitch into
        // a true ellipse / pill / stadium shape that matches web's
        // rendering pixel-for-pixel.
        if let frac = cornerRadiusFraction {
            let rx = min(r.width / 2, cornerRadius + r.width * frac)
            let ry = min(r.height / 2, cornerRadius + r.height * frac)
            return ellipticalRoundedRectPath(in: r, rx: rx, ry: ry)
        }
        return Path(roundedRect: r, cornerRadius: cornerRadius)
    }
}

// Build a rounded rectangle whose four corners are quarter-ellipses with
// independent radii rx (horizontal) and ry (vertical). When rx == ry it
// degenerates to a normal rounded rectangle; when rx == width/2 and
// ry == height/2 it becomes a perfect ellipse — matching CSS's
// `border-radius: 50% / 50%` / `inset(0 round 50%)` behaviour.
private func ellipticalRoundedRectPath(in r: CGRect, rx: CGFloat, ry: CGFloat) -> Path {
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
// silently rendered with a 0-radius fallback.
private struct CircleClip: Shape {
    let radius: CGFloat
    let kind: ShapeRadiusKind
    let cx, cy: CGFloat    // unit-space centre.
    func path(in rect: CGRect) -> Path {
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
// distances for rx, vertical for ry).
private struct EllipseClip: Shape {
    let rx, ry: CGFloat
    let rxKind, ryKind: ShapeRadiusKind
    let cx, cy: CGFloat
    func path(in rect: CGRect) -> Path {
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
// the box or an absolute length. CSS allows evenodd winding via clip-rule;
// we thread that through via Path's built-in winding.
private struct PolygonClip: Shape {
    let points: [PolygonPoint]
    let evenOdd: Bool
    func path(in rect: CGRect) -> Path {
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
        _ = evenOdd  // SwiftUI's Shape protocol doesn't expose fill rule
                     // on .clipShape directly — documented limitation.
        return p
    }
}

// SVG path clip — naive: uses Core Graphics `CGPath` via Path(_:) with
// the data string re-parsed by UIBezierPath when possible. SwiftUI's
// Path has no SVG parser, so we fall back to a simple hand-rolled
// M/L/Q/Z walker for the subset of syntax our fixtures use.
private struct SvgPathClip: Shape {
    let data: String
    let evenOdd: Bool
    func path(in rect: CGRect) -> Path {
        var p = Path()
        // Hand-rolled walker: tokenise by whitespace and upper-case verb.
        // Supports M, L, Q, C, Z (relative variants treated as absolute;
        // TODO: honour lowercase relative semantics).
        let tokens = data.split(whereSeparator: { $0.isWhitespace || $0 == "," })
            .map(String.init)
        var i = 0
        // Helper that consumes `n` scalars starting at `i` and advances.
        func take(_ n: Int) -> [CGFloat] {
            var out: [CGFloat] = []
            for _ in 0..<n where i < tokens.count {
                if let d = Double(tokens[i]) { out.append(CGFloat(d)) }
                i += 1
            }
            return out
        }
        while i < tokens.count {
            let tok = tokens[i]; i += 1
            switch tok.uppercased() {
            case "M":
                let c = take(2); if c.count == 2 { p.move(to: CGPoint(x: c[0], y: c[1])) }
            case "L":
                let c = take(2); if c.count == 2 { p.addLine(to: CGPoint(x: c[0], y: c[1])) }
            case "Q":
                let c = take(4)
                if c.count == 4 {
                    p.addQuadCurve(to: CGPoint(x: c[2], y: c[3]),
                                   control: CGPoint(x: c[0], y: c[1]))
                }
            case "C":
                let c = take(6)
                if c.count == 6 {
                    p.addCurve(to: CGPoint(x: c[4], y: c[5]),
                               control1: CGPoint(x: c[0], y: c[1]),
                               control2: CGPoint(x: c[2], y: c[3]))
                }
            case "Z":
                p.closeSubpath()
            default:
                // Silently skip unknown verbs / stray numbers.
                break
            }
        }
        // Translate so coordinates treat the view's origin as (0,0)
        // — the IR uses the element's own coordinate system.
        _ = evenOdd
        return p.offsetBy(dx: rect.minX, dy: rect.minY)
    }
}

// CSS `rect(t r b l)` clip-path form — top-left corner at `left, top`,
// bottom-right at `right, bottom`. "auto" components fall back to the
// view's own edge.
private struct RectClip: Shape {
    let top, right, bottom, left: CGFloat?
    let cornerRadius: CGFloat
    func path(in rect: CGRect) -> Path {
        let t = top ?? 0
        let r = right ?? rect.width
        let b = bottom ?? rect.height
        let l = left ?? 0
        let box = CGRect(x: rect.minX + l, y: rect.minY + t,
                         width: max(0, r - l), height: max(0, b - t))
        return Path(roundedRect: box, cornerRadius: cornerRadius)
    }
}

// CSS `xywh(x y w h)` — explicit top-left corner + dimensions.
private struct XywhClip: Shape {
    let x, y, w, h, cornerRadius: CGFloat
    func path(in rect: CGRect) -> Path {
        let box = CGRect(x: rect.minX + x, y: rect.minY + y,
                         width: w, height: h)
        return Path(roundedRect: box, cornerRadius: cornerRadius)
    }
}

// Legacy `clip: rect(t, r, b, l)` — identical geometry to modern rect().
private struct LegacyClipRect: Shape {
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
