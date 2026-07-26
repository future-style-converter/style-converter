//
//  TransformsEffects.swift
//  StyleEngine/transforms — split out of TransformsApplier.swift in
//  wave 20 (lane W4) purely for the repo's file-size rule (the applier
//  had grown past ~300 lines). NO behavior change: these are the
//  GeometryEffects the applier dispatches to (Rotate3DEffect /
//  TranslateEffect / SkewEffect) plus the pure TransformsMath the
//  XCTest suites pin — same module, same internal visibility, so every
//  existing reference (applier + FidelityWave3/5 tests) is untouched.
//

import SwiftUI
// CATransform3D — the 4x4 carrier Rotate3DEffect builds from.
import QuartzCore

// MARK: - True 3D rotation (fidelity wave 5)

/// CSS 3D rotation about an arbitrary axis with an OPTIONAL preceding
/// `perspective(d)` — as a GeometryEffect so the anchor resolves against
/// the laid-out size and the projective matrix reaches the renderer via
/// ProjectionTransform (the only SwiftUI channel that carries a real
/// perspective row).
///
/// css-transforms-2 §13.1: the rendered matrix is
///   T(origin) · P(d) · R(axis, θ) · T(−origin)
/// With d = ∞ (no perspective) the projection is ORTHOGRAPHIC — a bare
/// rotateY(θ) flattens to a pure |cosθ| horizontal scale, exactly what
/// browsers paint. SwiftUI's rotation3DEffect instead bakes in its own
/// default perspective (keystone CSS never drew) and its scale hack
/// replacement never drew the keystone CSS DOES draw for
/// `perspective(500px) rotateY(45deg)` — both halves of the wave-5 3D
/// cluster (i-w 0.69–0.78).
struct Rotate3DEffect: GeometryEffect {
    /// Rotation axis (CSS rotate3d components; magnitude-insensitive).
    var axisX: CGFloat
    var axisY: CGFloat
    var axisZ: CGFloat
    /// Rotation angle in CSS degrees (positive = clockwise looking down
    /// the axis vector, matching CATransform3DRotate).
    var deg: CGFloat
    /// Perspective distance in px from a preceding `perspective(d)` in
    /// the same transform list; nil = orthographic projection.
    var perspectivePx: CGFloat?
    /// Fractional transform-origin (UnitPoint, default .center).
    var anchor: UnitPoint
    /// Explicit px transform-origin overrides (see SkewEffect).
    var anchorXPx: CGFloat? = nil
    var anchorYPx: CGFloat? = nil

    // SwiftUI hands us the laid-out size — the CSS reference box.
    func effectValue(size: CGSize) -> ProjectionTransform {
        // Anchor point in local pixels (px origin wins over fraction).
        let cx = anchorXPx ?? size.width * anchor.x
        let cy = anchorYPx ?? size.height * anchor.y
        // §13.1 perspective matrix: m34 = −1/d. Identity when absent.
        var m = CATransform3DIdentity
        if let d = perspectivePx, d > 0 { m.m34 = -1 / d }
        // Rotation folded UNDER the perspective (P · R) — the canonical
        // Core Animation keystone recipe, same operand order as CSS.
        m = CATransform3DRotate(m, deg * .pi / 180, axisX, axisY, axisZ)
        // Conjugate about the anchor: T(c) · (P·R) · T(−c). Concat is
        // row-vector order, so the INNER translation comes first.
        let toOrigin   = CATransform3DMakeTranslation(-cx, -cy, 0)
        let fromOrigin = CATransform3DMakeTranslation(cx, cy, 0)
        let full = CATransform3DConcat(CATransform3DConcat(toOrigin, m), fromOrigin)
        // ProjectionTransform keeps the projective 2D slice (m14/m24/m44
        // survive), so the keystone reaches the rasteriser intact.
        return ProjectionTransform(full)
    }
}

// MARK: - Percentage translate (fidelity wave 5)

/// CSS translate with percentage components, as a GeometryEffect.
///
/// css-transforms-1 §6: translate percentages resolve against the
/// element's own border box (width for X, height for Y). GeometryEffect
/// receives the element's final laid-out size and never participates in
/// layout — unlike the greedy GeometryReader this replaces, which
/// resolved the fractions against the parent's proposed size (the
/// full capture canvas) and threw percentage-translated boxes to the
/// canvas edge (PW_Images_Transforms_02, iOS-only divergence).
struct TranslateEffect: GeometryEffect {
    /// Absolute pixel components (from px lengths in the IR).
    var x: CGFloat
    var y: CGFloat
    /// Fractional components: CSS `50%` → 0.5 of the own-box axis size.
    var xFrac: CGFloat
    var yFrac: CGFloat

    // Called by SwiftUI with the final laid-out size of the content —
    // exactly the CSS reference box for the percentage resolution.
    func effectValue(size: CGSize) -> ProjectionTransform {
        // Mix absolute pts with own-size-relative pts per component.
        ProjectionTransform(CGAffineTransform(
            translationX: x + size.width * xFrac,
            y: y + size.height * yFrac))
    }
}

// MARK: - Anchored skew (fidelity wave 4)

/// CSS skew about `transform-origin`, as a GeometryEffect.
///
/// GeometryEffect is the right vehicle: SwiftUI hands it the element's
/// laid-out size (so the fractional anchor resolves to pixels exactly
/// like rotate/scale's UnitPoint anchors) and the effect never
/// participates in layout — unlike the GeometryReader wrapping the
/// percentage-translate path, it cannot change the view's proposed size.
struct SkewEffect: GeometryEffect {
    // Skew angles in CSS degrees (skewX(θ) → xDeg, skewY(θ) → yDeg).
    var xDeg: CGFloat
    var yDeg: CGFloat
    // Fractional transform-origin — same UnitPoint rotate/scale use;
    // defaults to .center upstream, matching the CSS 50% 50% initial.
    var anchor: UnitPoint
    // Wave 5: explicit PX origin components (transform-origin: 30px …).
    // Non-nil overrides the fractional anchor on that axis — resolved
    // against the laid-out size here, the only place it is known.
    var anchorXPx: CGFloat? = nil
    var anchorYPx: CGFloat? = nil

    // Called by SwiftUI with the final laid-out size of the content.
    func effectValue(size: CGSize) -> ProjectionTransform {
        // Delegate the spec arithmetic to TransformsMath so XCTest can
        // pin the matrix without instantiating a view hierarchy.
        ProjectionTransform(TransformsMath.anchoredSkew(
            xDeg: xDeg, yDeg: yDeg, size: size, anchor: anchor,
            anchorXPx: anchorXPx, anchorYPx: anchorYPx))
    }
}

// MARK: - Pure transform math (fidelity wave 3)

/// Spec arithmetic split out of the view code so XCTest can pin it.
enum TransformsMath {
    /// Scale factor the perspective chain applies to this element.
    ///
    /// FLAT content (no 3D rotation): the §13.1 perspective matrix maps
    /// (x, y, 0, 1) to (x, y, 0, 1 − 0/d) — identity for EVERY finite
    /// positive distance. Pinned by FidelityWave3Tests because the old
    /// applier unconditionally scaled by max(0.9, d/1000): a visible
    /// ~10% shrink at `perspective: 500px` that web/Android never
    /// render (wave-3 measurement, Transforms_TextBlock 0.757).
    ///
    /// 3D-ROTATED element: legacy depth-feel foreshortening
    /// max(0.9, d/1000) — SwiftUI has no true perspective divide on a
    /// 2D view chain, and the committed 046_Perspective_Rotate iOS
    /// baseline pins this approximation exactly.
    static func perspectiveScale(distancePx: CGFloat,
                                 has3DRotation: Bool) -> CGFloat {
        // Flat plane → spec-exact identity; degenerate d ≤ 0 too.
        guard has3DRotation, distancePx > 0 else { return 1.0 }
        // Legacy baseline-pinned approximation for 3D-rotated content.
        return max(0.9, min(1.0, distancePx / 1000.0))
    }

    /// CSS skew matrix conjugated about the transform-origin anchor
    /// (fidelity wave 4 — the skewX placement fix).
    ///
    /// css-transforms-1 §8 defines the rendered matrix as
    ///   T(origin) · S · T(−origin)
    /// with the raw shear S = [1 tanθx; tanθy 1] (§13, skew()). The
    /// linear 2×2 part survives conjugation unchanged; only a constant
    /// translation appears:
    ///   offset = c − S·c = (−tanθx·cy, −tanθy·cx)
    /// where c = (anchor.x·w, anchor.y·h). For the default centre
    /// anchor this cancels exactly the +tan(θ)·h/2 drift a top-left
    /// application produces — the measured +4–5px on the skewX(8deg)
    /// Decorated rows (tan 8° ≈ 0.1405, ×32 ≈ 4.5px on a 64px box).
    static func anchoredSkew(xDeg: CGFloat, yDeg: CGFloat,
                             size: CGSize, anchor: UnitPoint,
                             anchorXPx: CGFloat? = nil,
                             anchorYPx: CGFloat? = nil) -> CGAffineTransform {
        // Shear tangents from the CSS angles (degrees → radians → tan).
        let tx = tan(xDeg * .pi / 180)
        let ty = tan(yDeg * .pi / 180)
        // Anchor point in local pixels — fractional UnitPoint × size,
        // unless the origin declared an explicit LENGTH on that axis
        // (wave 5: `transform-origin: 30px 25%` anchors at x = 30px
        // regardless of the box width, css-transforms-1 §8).
        let cx = anchorXPx ?? size.width * anchor.x
        let cy = anchorYPx ?? size.height * anchor.y
        // Closed form of T(c) · S · T(−c): shear + compensating offset.
        return CGAffineTransform(a: 1, b: ty, c: tx, d: 1,
                                 tx: -tx * cy, ty: -ty * cx)
    }

    /// True when the aggregate carries a rotation OUT of the z = 0
    /// plane — any rotate function (list or longhand) with a non-zero
    /// X or Y axis component. Bare 2D `rotate(deg)` is (0,0,1) and
    /// stays flat.
    static func has3DRotation(_ agg: TransformsAggregate) -> Bool {
        // Function list + longhand rotate, one scan.
        let all = agg.functions + [agg.rotate].compactMap { $0 }
        for fn in all {
            if case .rotate(let x, let y, _, let d) = fn,
               d != 0, (x != 0 || y != 0) { return true }
        }
        return false
    }
}
