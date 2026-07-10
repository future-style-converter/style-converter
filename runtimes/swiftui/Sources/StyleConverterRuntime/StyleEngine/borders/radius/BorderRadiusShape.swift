//
//  BorderRadiusShape.swift
//  StyleEngine/borders/radius — Phase 5.
//
//  Custom Shape that honours four independent elliptical corners.
//  SwiftUI's `RoundedRectangle(cornerRadius:)` is uniform-only and
//  `UnevenRoundedRectangle` (iOS 16+) supports per-corner square radii
//  but not elliptical. We draw the path by hand so CSS's "20px 10px"
//  corners render faithfully.
//
//  Reference:
//    CSS Backgrounds 3 §5.4  — border-radius path construction.
//    Apple docs — Path.addArc(tangent1End:tangent2End:radius:)
//    which approximates elliptical arcs via a scaled transform.
//

// SwiftUI for the Shape protocol.
import SwiftUI

// Trim-safe, animatable-ignoring Shape. We don't support interpolation
// across mismatched corner counts (no `animatableData`) — radius is
// mostly static in CSS.
struct BorderRadiusShape: InsettableShape {
    // Corner bundle produced by BorderRadiusExtractor.
    var radius: BorderRadiusConfig
    // Inset distance — populated by `.strokeBorder(…)` so the stroke sits
    // inside the drawn rectangle instead of straddling its edge. Defaults
    // to zero for the plain `.fill` / `.clipShape` paths.
    var inset: CGFloat = 0

    // InsettableShape: return a copy with the inset increased. The rect
    // shrinks inside `path(in:)` by `inset` on every side, and each
    // corner radius decreases by the same amount (clamped to zero) so
    // the stroked path stays parallel to the outline.
    func inset(by amount: CGFloat) -> BorderRadiusShape {
        var copy = self
        copy.inset += amount
        return copy
    }

    // Walk the four corners clockwise from the top-left. Each leg draws:
    //   1. A straight line to the next corner's tangent point.
    //   2. An elliptical arc into the corner using an axis-scaled
    //      GraphicsContext so we can reuse `addArc(center:radius:...)`.
    func path(in rect: CGRect) -> Path {
        // Apply the stroke-inset so `.strokeBorder(lineWidth: w)` draws
        // fully inside the element instead of straddling the perimeter.
        let rect = rect.insetBy(dx: inset, dy: inset)
        var p = Path()
        // CSS Backgrounds 3 §5.4 corner-overflow normalisation. The naïve
        // per-axis clamp (`x → halfW`, `y → halfH`) handles corners in
        // isolation but fails when the SUM of two adjacent corners exceeds
        // the shared side length. With `border-radius: 9999px` on a 200×100
        // box, every corner clamps to (100, 50) → top/bottom edges have
        // zero straight segment between them and the shape becomes a pure
        // ellipse instead of the expected pill capsule (Edge_VeryLargeRadius
        // rendered as flat 200×100 ellipse on iOS while web/Android both
        // produced the correct capsule). The spec fix is a single global
        // scale factor `f = min(W / (TL.x + TR.x), H / (TR.y + BR.y),
        // W / (BL.x + BR.x), H / (TL.y + BL.y))`; if f < 1 multiply every
        // corner by f. With 9999px on 200×100 this gives f = 100/19998 =
        // 0.005, scaling each radius to 50 — half the short side, the
        // standard pill capsule.
        let shrink: (CGFloat) -> CGFloat = { max(0, $0 - self.inset) }
        // Resolve percent axes FIRST — CSS Backgrounds 3 §5.1 says a
        // percent horizontal radius refers to the border-box WIDTH and
        // a percent vertical radius to its HEIGHT. Resolution against
        // the un-inset rect (the border box) keeps `border-radius: 50%`
        // an exact half-width/half-height ellipse; the stroke inset is
        // subtracted afterwards like any other px radius.
        let full = CGSize(width: rect.width + inset * 2,
                          height: rect.height + inset * 2)
        let pTL = radius.topLeft.resolved(in: full)
        let pTR = radius.topRight.resolved(in: full)
        let pBR = radius.bottomRight.resolved(in: full)
        let pBL = radius.bottomLeft.resolved(in: full)
        let rTL = BorderRadiusCorner(x: shrink(pTL.x), y: shrink(pTL.y))
        let rTR = BorderRadiusCorner(x: shrink(pTR.x), y: shrink(pTR.y))
        let rBR = BorderRadiusCorner(x: shrink(pBR.x), y: shrink(pBR.y))
        let rBL = BorderRadiusCorner(x: shrink(pBL.x), y: shrink(pBL.y))
        let scaleFactor = sideScaleFactor(rTL: rTL, rTR: rTR,
                                          rBR: rBR, rBL: rBL,
                                          width: rect.width,
                                          height: rect.height)
        let tl = scale(rTL, by: scaleFactor)
        let tr = scale(rTR, by: scaleFactor)
        let br = scale(rBR, by: scaleFactor)
        let bl = scale(rBL, by: scaleFactor)

        // Start at the top edge, just right of the top-left curve.
        p.move(to: CGPoint(x: rect.minX + tl.x, y: rect.minY))
        // Top edge → start of top-right curve.
        p.addLine(to: CGPoint(x: rect.maxX - tr.x, y: rect.minY))
        // Top-right elliptical quarter.
        addEllipticalCorner(&p,
                            from: CGPoint(x: rect.maxX - tr.x, y: rect.minY),
                            to:   CGPoint(x: rect.maxX,        y: rect.minY + tr.y),
                            center: CGPoint(x: rect.maxX - tr.x, y: rect.minY + tr.y),
                            radiusX: tr.x, radiusY: tr.y,
                            startAngle: .degrees(270), endAngle: .degrees(360))
        // Right edge → start of bottom-right curve.
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - br.y))
        addEllipticalCorner(&p,
                            from: CGPoint(x: rect.maxX,        y: rect.maxY - br.y),
                            to:   CGPoint(x: rect.maxX - br.x, y: rect.maxY),
                            center: CGPoint(x: rect.maxX - br.x, y: rect.maxY - br.y),
                            radiusX: br.x, radiusY: br.y,
                            startAngle: .degrees(0), endAngle: .degrees(90))
        // Bottom edge → start of bottom-left curve.
        p.addLine(to: CGPoint(x: rect.minX + bl.x, y: rect.maxY))
        addEllipticalCorner(&p,
                            from: CGPoint(x: rect.minX + bl.x, y: rect.maxY),
                            to:   CGPoint(x: rect.minX,        y: rect.maxY - bl.y),
                            center: CGPoint(x: rect.minX + bl.x, y: rect.maxY - bl.y),
                            radiusX: bl.x, radiusY: bl.y,
                            startAngle: .degrees(90), endAngle: .degrees(180))
        // Left edge → back to top-left tangent.
        p.addLine(to: CGPoint(x: rect.minX, y: rect.minY + tl.y))
        addEllipticalCorner(&p,
                            from: CGPoint(x: rect.minX,        y: rect.minY + tl.y),
                            to:   CGPoint(x: rect.minX + tl.x, y: rect.minY),
                            center: CGPoint(x: rect.minX + tl.x, y: rect.minY + tl.y),
                            radiusX: tl.x, radiusY: tl.y,
                            startAngle: .degrees(180), endAngle: .degrees(270))
        p.closeSubpath()
        return p
    }

    // CSS Backgrounds 3 §5.4 step 1: derive the global scale factor.
    // For each side, the sum of the two touching corner radii along that
    // axis must not exceed the side's length. We compute the worst-case
    // ratio and return min(1, all ratios) — when ≥ 1 nothing scales,
    // when < 1 every radius gets multiplied by it (step 2 below).
    // Sides:
    //   top    : tl.x + tr.x ≤ width
    //   right  : tr.y + br.y ≤ height
    //   bottom : bl.x + br.x ≤ width
    //   left   : tl.y + bl.y ≤ height
    private func sideScaleFactor(rTL: BorderRadiusCorner,
                                 rTR: BorderRadiusCorner,
                                 rBR: BorderRadiusCorner,
                                 rBL: BorderRadiusCorner,
                                 width: CGFloat,
                                 height: CGFloat) -> CGFloat {
        // Helper: ratio of side length to corner-pair sum, or +∞ when the
        // pair sums to zero (no constraint, no scaling required).
        func ratio(_ side: CGFloat, _ a: CGFloat, _ b: CGFloat) -> CGFloat {
            let sum = a + b
            return sum > 0 ? side / sum : .infinity
        }
        let f = min(
            ratio(width,  rTL.x, rTR.x),
            ratio(height, rTR.y, rBR.y),
            ratio(width,  rBL.x, rBR.x),
            ratio(height, rTL.y, rBL.y)
        )
        // Spec: only scale DOWN — radii smaller than the side don't need
        // adjustment and CSS doesn't grow them.
        return min(1, f)
    }

    // Apply the global scale factor and clamp negatives. The factor is
    // already ≤ 1, so per-axis values can never grow past their original
    // (and original was ≥ 0 by construction).
    private func scale(_ c: BorderRadiusCorner, by f: CGFloat) -> BorderRadiusCorner {
        BorderRadiusCorner(x: max(0, c.x * f),
                           y: max(0, c.y * f))
    }

    // Draw an elliptical corner by translating + scaling so the arc is
    // a plain circle, then adding a quarter-arc. Avoids wrestling with
    // `Path.addArc(tangent1End:…)` which only does circular tangent arcs.
    // When both radii are zero, drop to a line corner so the path stays
    // sharp.
    //
    // Crucial: we call `Path.addArc(...transform:)` *directly on p* rather
    // than building a side `Path()` and splicing it in via
    // `p.addPath(arc, transform:)`. The detour was an iOS rounded-corner
    // bug: `addPath` appends a new subpath (with its own implicit move)
    // instead of continuing the current one, so each corner became a
    // disjoint subpath. Avatar_Circle / Card_Complete / Input_Field /
    // BorderRadius_Uniform / Tag_Chip all rendered as diamond/polygon
    // outlines with the bg fill missing — fill couldn't fuse the
    // 5 detached subpaths into a closed region. CoreGraphics
    // CGPathAddArc with a non-empty path implicitly adds a line from the
    // current point to the (transformed) arc start, preserving subpath
    // continuity.
    private func addEllipticalCorner(_ p: inout Path,
                                     from a: CGPoint, to b: CGPoint,
                                     center: CGPoint,
                                     radiusX: CGFloat, radiusY: CGFloat,
                                     startAngle: Angle, endAngle: Angle) {
        if radiusX <= 0 || radiusY <= 0 {
            // Sharp corner — step the path to the next tangent via a
            // straight segment through the corner. `a` and `b` collapse
            // to the same point when radii are zero (per clamp) so this
            // is effectively a no-op move.
            p.addLine(to: b)
            return
        }
        // Translate + non-uniform-scale: the unit-circle arc becomes a
        // true elliptical quarter when CoreGraphics multiplies each
        // sampled arc point by this matrix. Same shape as before; the
        // change is *which* path receives the arc.
        let transform = CGAffineTransform(translationX: center.x, y: center.y)
            .scaledBy(x: radiusX, y: radiusY)
        p.addArc(center: .zero, radius: 1,
                 startAngle: startAngle, endAngle: endAngle,
                 clockwise: false, transform: transform)
    }
}
