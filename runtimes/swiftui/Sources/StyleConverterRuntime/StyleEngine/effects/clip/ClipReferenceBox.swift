//
//  ClipReferenceBox.swift
//  StyleEngine/effects/clip — wave 46 (lane Y4).
//
//  css-masking-1 §7.1 REFERENCE BOX resolution for `clip-path` on iOS.
//  Every `<basic-shape>` (and the bare `<geometry-box>` form) resolves
//  against a box chosen by the `<geometry-box>` keyword: margin-box /
//  border-box (the default) / padding-box / content-box. The rect a
//  SwiftUI `Shape.path(in:)` receives from `.clipShape` is the BORDER box
//  — `.engineClipPath` sits inside `engineSpacingMargin` and outside the
//  padding / border paint (StyleBuilder.applyGroupEffects) — so the other
//  three derive from it with the element's own metrics. Byte-parallel twin
//  of Android's ClipReferenceBox.kt (which additionally has to find the
//  border box inside a margin-inflated node; iOS does not).
//  Measured on wave45-final: contentBox-1a clipped a 180px circle (border
//  box) where the ref has the 100px content-box circle; geometryBox-2's
//  `polygon(…) margin-box` kept only the roof; marginBox-1b/1c/1d's 200px
//  outline flooded 291px of green because the keyword form was a no-op.
//

import SwiftUI

// One resolved reference box: its rect (clip-rect space) + corner curves.
struct ClipReferenceFrame: Equatable {
    let rect: CGRect
    let topLeft: BorderRadiusCorner
    let topRight: BorderRadiusCorner
    let bottomRight: BorderRadiusCorner
    let bottomLeft: BorderRadiusCorner
}

enum ClipReferenceBox {

    /// The reference box `box` of an element whose border box is `rect`.
    /// Padding / content boxes inset by the used border widths and the
    /// paddings (css-backgrounds-3 §5.1 inner radii = outer − width); the
    /// margin box outsets by the DECLARED margins with the css-shapes-1
    /// §4 corner rule. With `.none` metrics every box equals `rect` and
    /// carries square corners — byte-identical to the pre-wave-46 shapes.
    static func resolve(_ box: ClipGeometryBox, metrics m: ClipBoxMetrics,
                        in rect: CGRect) -> ClipReferenceFrame {
        let radii = borderRadii(m.radius, in: rect)
        switch box {
        case .borderBox:
            return ClipReferenceFrame(rect: rect, topLeft: radii[0], topRight: radii[1],
                                      bottomRight: radii[2], bottomLeft: radii[3])
        case .paddingBox:
            return inset(rect, radii, t: m.borderTop, r: m.borderRight,
                         b: m.borderBottom, l: m.borderLeft)
        case .contentBox:
            return inset(rect, radii,
                         t: m.borderTop + m.paddingTop, r: m.borderRight + m.paddingRight,
                         b: m.borderBottom + m.paddingBottom, l: m.borderLeft + m.paddingLeft)
        case .marginBox:
            let out = CGRect(x: rect.minX - m.marginLeft, y: rect.minY - m.marginTop,
                             width: rect.width + m.marginLeft + m.marginRight,
                             height: rect.height + m.marginTop + m.marginBottom)
            // Per corner: the horizontal axis takes that side's horizontal
            // margin, the vertical axis the vertical one.
            func corner(_ c: BorderRadiusCorner, _ mx: CGFloat, _ my: CGFloat) -> BorderRadiusCorner {
                BorderRadiusCorner(x: marginOutsetRadius(c.x, mx), y: marginOutsetRadius(c.y, my))
            }
            return ClipReferenceFrame(
                rect: out,
                topLeft: corner(radii[0], m.marginLeft, m.marginTop),
                topRight: corner(radii[1], m.marginRight, m.marginTop),
                bottomRight: corner(radii[2], m.marginRight, m.marginBottom),
                bottomLeft: corner(radii[3], m.marginLeft, m.marginBottom))
        }
    }

    /// css-shapes-1 §4 margin-box corner rule: a corner radius r outset by
    /// a margin m is `r + m` when m ≤ 0 or r/m ≥ 1, else
    /// `r + m·(1 + (r/m − 1)³)` — a square corner (r = 0) STAYS square
    /// while a large radius grows by the full margin (WPT marginBox-1c:
    /// 50 + 25 = 75 → full circle; marginBox-1d: 10 + 50·(1 + (0.2 − 1)³)
    /// = 34.4).
    static func marginOutsetRadius(_ r: CGFloat, _ m: CGFloat) -> CGFloat {
        if m <= 0 || r >= m { return max(0, r + m) }
        let d = r / m - 1
        return max(0, r + m * (1 + d * d * d))
    }

    /// Inner-box curves: each axis loses the band on its side, floored at 0.
    private static func inset(_ outer: CGRect, _ radii: [BorderRadiusCorner],
                              t: CGFloat, r: CGFloat, b: CGFloat, l: CGFloat) -> ClipReferenceFrame {
        let rect = CGRect(x: outer.minX + l, y: outer.minY + t,
                          width: max(0, outer.width - l - r),
                          height: max(0, outer.height - t - b))
        func shrink(_ c: BorderRadiusCorner, _ dx: CGFloat, _ dy: CGFloat) -> BorderRadiusCorner {
            BorderRadiusCorner(x: max(0, c.x - dx), y: max(0, c.y - dy))
        }
        return ClipReferenceFrame(
            rect: rect,
            topLeft: shrink(radii[0], l, t), topRight: shrink(radii[1], r, t),
            bottomRight: shrink(radii[2], r, b), bottomLeft: shrink(radii[3], l, b))
    }

    /// Border-box corner curves, order TL / TR / BR / BL: percent axes
    /// resolve against the border box (css-backgrounds-3 §5.1 via
    /// BorderRadiusCorner.resolved), then the §5.1 overlap rule scales ALL
    /// radii by the smallest `side / (sum of adjacent radii)` ratio below 1.
    private static func borderRadii(_ cfg: BorderRadiusConfig, in rect: CGRect) -> [BorderRadiusCorner] {
        let tl = cfg.topLeft.resolved(in: rect.size)
        let tr = cfg.topRight.resolved(in: rect.size)
        let br = cfg.bottomRight.resolved(in: rect.size)
        let bl = cfg.bottomLeft.resolved(in: rect.size)
        func ratio(_ extent: CGFloat, _ sum: CGFloat) -> CGFloat {
            (sum > extent && sum > 0) ? extent / sum : 1
        }
        let f = min(min(ratio(rect.width, tl.x + tr.x), ratio(rect.width, bl.x + br.x)),
                    min(ratio(rect.height, tl.y + bl.y), ratio(rect.height, tr.y + br.y)))
        func scaled(_ c: BorderRadiusCorner) -> BorderRadiusCorner {
            f < 1 ? BorderRadiusCorner(x: c.x * f, y: c.y * f) : c
        }
        return [scaled(tl), scaled(tr), scaled(br), scaled(bl)]
    }

    /// A rounded rectangle with independent elliptical corners — the clip
    /// region for the bare `<geometry-box>` form. Four quarter-ellipse
    /// arcs stitched to four edges as ONE closed contour; degenerates to
    /// `Path(rect)` when every corner is square. SwiftUI's y-down
    /// `addArc(clockwise: false)` sweeps with INCREASING angle (visually
    /// clockwise), so each corner runs from its incoming edge to its
    /// outgoing edge: TR −π/2→0, BR 0→π/2, BL π/2→π, TL π→3π/2.
    static func roundedPath(_ f: ClipReferenceFrame) -> Path {
        let r = f.rect
        if f.topLeft.isSquare && f.topRight.isSquare && f.bottomRight.isSquare && f.bottomLeft.isSquare {
            return Path(r)
        }
        var p = Path()
        // Corner helper: one quarter ellipse of radii (rx, ry) centred at c,
        // sweeping +π/2 from `from` (radians, y-down so −π/2 is "up"). Built
        // as a unit-circle arc under a non-uniform scale — CoreGraphics'
        // own Bézier arc approximation, no hand-rolled control points —
        // then its curve elements are appended to the CURRENT subpath
        // (the arc's own leading move is dropped) so the outline stays one
        // closed contour.
        func arc(_ c: CGPoint, _ rx: CGFloat, _ ry: CGFloat, from: Double) {
            guard rx > 0, ry > 0 else { return }
            var unit = Path()
            unit.addArc(center: .zero, radius: 1, startAngle: .radians(from),
                        endAngle: .radians(from + .pi / 2), clockwise: false)
            let t = CGAffineTransform(translationX: c.x, y: c.y).scaledBy(x: rx, y: ry)
            unit.applying(t).forEach { element in
                switch element {
                case .move: break
                case .line(let to): p.addLine(to: to)
                case .quadCurve(let to, let control): p.addQuadCurve(to: to, control: control)
                case .curve(let to, let c1, let c2): p.addCurve(to: to, control1: c1, control2: c2)
                case .closeSubpath: break
                }
            }
        }
        p.move(to: CGPoint(x: r.minX + f.topLeft.x, y: r.minY))
        p.addLine(to: CGPoint(x: r.maxX - f.topRight.x, y: r.minY))
        arc(CGPoint(x: r.maxX - f.topRight.x, y: r.minY + f.topRight.y), f.topRight.x, f.topRight.y, from: -.pi / 2)
        p.addLine(to: CGPoint(x: r.maxX, y: r.maxY - f.bottomRight.y))
        arc(CGPoint(x: r.maxX - f.bottomRight.x, y: r.maxY - f.bottomRight.y), f.bottomRight.x, f.bottomRight.y, from: 0)
        p.addLine(to: CGPoint(x: r.minX + f.bottomLeft.x, y: r.maxY))
        arc(CGPoint(x: r.minX + f.bottomLeft.x, y: r.maxY - f.bottomLeft.y), f.bottomLeft.x, f.bottomLeft.y, from: .pi / 2)
        p.addLine(to: CGPoint(x: r.minX, y: r.minY + f.topLeft.y))
        arc(CGPoint(x: r.minX + f.topLeft.x, y: r.minY + f.topLeft.y), f.topLeft.x, f.topLeft.y, from: .pi)
        p.closeSubpath()
        return p
    }
}
