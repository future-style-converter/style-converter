//
//  SingularTransform.swift
//  StyleEngine/transforms — wave 35 (lane B1).
//
//  css-transforms-1 §8: "If a transform function causes the current
//  transformation matrix of an object to be non-invertible, the object and
//  its content do not get displayed." A rotateX/rotateY of exactly ±90°
//  (or ±270°) turns the box EDGE-ON: its 2D projection collapses to a
//  line, the matrix is non-invertible, and every browser paints nothing.
//
//  ── The measured iOS defect this file repairs ──────────────────────────
//  css-transforms/3d-rendering-context-and-inline expects a BLANK page
//  (`.outer` rotateX(90deg) preserve-3d, `.inner` rotateX(-90deg) red
//  100×100 inside a display:inline middle box). Web and Android render
//  blank and PASS; iOS rendered a FULL, AXIS-ALIGNED, UNSCALED red square
//  and failed at 0.9554. That signature is not a 3D-composition bug — it
//  is SwiftUI declining a degenerate matrix. `Rotate3DEffect` returns
//  `ProjectionTransform(CATransform3D)`, whose 2D slice for rotateX(90°)
//  is [[1,0,0],[0,0,0],[0,0,1]] — determinant 0 — and SwiftUI falls back
//  to IDENTITY for a non-invertible projection rather than collapsing the
//  view. Both rotations therefore drew unrotated, and the red square
//  appeared at full size. Compose has no equivalent hole: a
//  `graphicsLayer { rotationX = 90f }` layer really is rasterised
//  edge-on, which is why Android already passes the same cell.
//
//  ── Why the predicate is size- and origin-INDEPENDENT ──────────────────
//  The rendered matrix is T(origin) · P(d) · R(axis, θ) · T(−origin), and
//  the transform-origin conjugation cannot change singularity. Verified
//  numerically before the change rather than argued
//  (_diag35/laneB1/singular-math.mjs replays the exact CATransform3D
//  arithmetic): rotateX(90°) has |det| = 6.1e-17 at anchors (50,50),
//  (0,0) and (17,93) alike, with and without a perspective(1000px)
//  prefix, while rotateY(45°) is 0.707 and rotateY(180°) is −1.0. So the
//  decision can live here, outside the GeometryEffect, where it can
//  actually hide the view.
//
//  ── Blast radius, enumerated BEFORE the change ─────────────────────────
//  _diag35/laneB1/scan-singular.mjs replays this predicate over all 29
//  frozen wave34-final sections' per-test IR (326 tests): exactly TWO
//  boxes are singular, and both are the target test's own
//  (3d-rendering-context-and-inline's `.outer` and `.inner`). No
//  dark-stage fixture carries a ±90° X/Y rotation — the committed
//  baselines (044_Transform_Rotate, 046_Perspective_Rotate, …) are all
//  well away from the degenerate angles, so this file cannot move them.
//
//  SCOPE, stated rather than silent: only the ROTATE family is tested.
//  A `scale(0)` and a `matrix()` with zero determinant are equally
//  non-invertible per §8, but neither appears in the corpus and neither
//  exhibits the SwiftUI fallback (a zero `.scaleEffect` genuinely
//  collapses), so they are deliberately left out rather than fixed blind.
//

import SwiftUI
// CATransform3D — the same 4×4 carrier Rotate3DEffect builds from, so the
// determinant is taken on the matrix that is actually rendered.
import QuartzCore

/// Is the used transform non-invertible (css-transforms-1 §8)?
enum SingularTransform {

    /// Determinant magnitude below which the projection counts as
    /// degenerate. cos(90°) evaluates to ~6.1e-17 in Double, so the exact
    /// cases land ~1e-16 while the nearest MEANINGFUL angle a test could
    /// author (89.999°) still yields ~1.7e-5 — five orders of magnitude of
    /// headroom. Chosen so a nearly-edge-on box still renders its sliver,
    /// exactly as the browser does.
    static let epsilon: CGFloat = 1e-9

    /// True when ANY rotate in the aggregate (function list or the
    /// `rotate` longhand) renders a non-invertible 2D projection.
    ///
    /// One rotation is enough: the composed chain multiplies the per-
    /// function matrices, and a product with a singular factor is singular.
    /// That mirrors how the applier emits them too — one modifier per
    /// function — so hiding on the first degenerate factor collapses the
    /// same view the browser declines to paint.
    static func isSingular(_ agg: TransformsAggregate) -> Bool {
        // The perspective SEED the applier uses (Step 1): a co-located
        // `perspective` property primes the first 3D rotation. Tracked
        // through the list so a perspective() FUNCTION overrides it,
        // exactly like TransformsApplier.body does.
        var pending: CGFloat? = agg.perspective?.distancePx
        for fn in agg.functions {
            if case .perspective(let d) = fn { pending = d; continue }
            if case .rotate(let x, let y, let z, let deg) = fn,
               isSingularRotation(axisX: x, axisY: y, axisZ: z,
                                  deg: deg, perspectivePx: pending) {
                return true
            }
        }
        // The `rotate` LONGHAND renders orthographically (the applier
        // passes no perspective for it — see TransformsApplier Step 2), so
        // it is tested with a nil distance.
        if case .rotate(let x, let y, let z, let deg)? = agg.rotate,
           isSingularRotation(axisX: x, axisY: y, axisZ: z,
                              deg: deg, perspectivePx: nil) {
            return true
        }
        return false
    }

    /// The per-rotation test: rebuild the EXACT matrix `Rotate3DEffect`
    /// builds (perspective row first, rotation folded under it) and take
    /// the determinant of the 2D projective slice `ProjectionTransform`
    /// keeps — rows/columns {1, 2, 4} of the 4×4.
    ///
    /// The transform-origin conjugation is deliberately omitted: it is a
    /// pair of pure translations, and the numeric probe above confirms the
    /// slice determinant is invariant under it. Leaving it out is what
    /// makes this predicate independent of the laid-out size, so the
    /// applier can decide before layout.
    static func isSingularRotation(axisX: CGFloat, axisY: CGFloat,
                                   axisZ: CGFloat, deg: CGFloat,
                                   perspectivePx: CGFloat?) -> Bool {
        // A zero angle or a null axis is the identity — never singular,
        // and CATransform3DRotate would divide by a zero axis length.
        guard deg != 0, axisX != 0 || axisY != 0 || axisZ != 0 else { return false }
        // §13.1 perspective matrix: m34 = −1/d. Identity when absent.
        var m = CATransform3DIdentity
        if let d = perspectivePx, d > 0 { m.m34 = -1 / d }
        // Rotation folded UNDER the perspective (P · R) — the same operand
        // order Rotate3DEffect uses, so this is the rendered matrix.
        m = CATransform3DRotate(m, deg * .pi / 180, axisX, axisY, axisZ)
        return abs(projectionSliceDeterminant(m)) < epsilon
    }

    /// Determinant of the 3×3 slice `ProjectionTransform(CATransform3D)`
    /// keeps — the projective 2D map (m11 m12 m14 / m21 m22 m24 /
    /// m41 m42 m44). This, not the 4×4 determinant, is what SwiftUI must
    /// invert, and therefore what decides whether it renders at all.
    static func projectionSliceDeterminant(_ m: CATransform3D) -> CGFloat {
        // Row-major expansion along the first row of the sliced matrix.
        m.m11 * (m.m22 * m.m44 - m.m24 * m.m42)
            - m.m12 * (m.m21 * m.m44 - m.m24 * m.m41)
            + m.m14 * (m.m21 * m.m42 - m.m22 * m.m41)
    }
}
