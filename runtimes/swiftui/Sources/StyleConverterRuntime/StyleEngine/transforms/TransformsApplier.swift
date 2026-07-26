//
//  TransformsApplier.swift
//  StyleEngine/transforms — Phase 8.
//
//  Consumes a `TransformsAggregate` and emits the matching SwiftUI
//  modifier chain. Ordering matters — CSS composes functions so that
//  the **first** token in `transform: a b c` is applied innermost
//  (closest to the element), and a modifier in SwiftUI also wraps its
//  content. Therefore we iterate `agg.functions` in declared order,
//  which means the first function ends up closest to the content view.
//
//  After the explicit `transform` list, CSS stacks the longhand
//  overrides in this order: translate → rotate → scale (matches the
//  spec text "the properties are applied as if the transform property
//  were given the value of translate, rotate, scale"). We follow that
//  exact ordering.
//

import SwiftUI
// CATransform3D for the wave-5 true 3D rotation (Rotate3DEffect).
import QuartzCore

struct TransformsApplier: ViewModifier {
    // Optional — nil means identity; the View extension below forwards
    // nil straight through, so there's no per-view cost on the common path.
    let config: TransformsAggregate?

    // Wave 19 (RC-B4a) — the accumulated 3D matrix of the containing 3D
    // rendering context (BackfaceCulling.swift). Identity default = no
    // context above; publishing/reset happens in Step 4 below.
    @Environment(\.transforms3DAccumulated) private var inherited3D: CATransform3D

    func body(content: Content) -> some View {
        // Short-circuit: nothing to do, return content as-is.
        guard let c = config, c.touched else { return AnyView(content) }

        // Pre-compute the effective anchor for rotate/scale. SwiftUI
        // defaults these to `.center`; we override when TransformOrigin
        // supplied keywords or percentages.
        let anchor = c.origin?.unit ?? .center

        // Step 1 — apply the CSS transform function list in declared
        // order. Each function wraps the current view so the first one
        // in the list ends up innermost. We fold via `reduce` into AnyView.
        // Wave 5: a `perspective(d)` FUNCTION is not rendered by itself —
        // §13.1: applied to flat content it is exactly identity — it
        // primes the projective divide for the 3D rotation that follows
        // it in the same list (`perspective(500px) rotateY(45deg)` is
        // THE canonical pairing). Track the pending distance and feed it
        // to the next 3D rotate, which renders the true keystone via
        // Rotate3DEffect instead of the old flat scale approximation.
        var v: AnyView = AnyView(content)
        // Seed from the `perspective` PROPERTY: the web reference
        // (engine/transforms/_dispatch.ts) deliberately folds a
        // co-located perspective length into the element's OWN
        // transform — `perspective: 500px` + `transform: rotateY(30deg)`
        // renders the keystone on the element itself across all three
        // runtimes (convergence decision; strict spec would only
        // project the children). A perspective() FUNCTION in the list
        // overrides the seed below.
        var pendingPerspective: CGFloat? = (c.perspective?.distancePx).map { CGFloat($0) }
        for fn in c.functions {
            if case .perspective(let d) = fn {
                // Stash for the following 3D rotation; nothing to draw
                // for the function itself (identity on the z = 0 plane).
                pendingPerspective = d
                continue
            }
            v = AnyView(applyFunction(fn, to: v, anchor: anchor,
                                      perspectivePx: pendingPerspective))
        }

        // Step 2 — longhand overrides, in CSS spec order: translate,
        // rotate, scale. Any of the three may be nil (not declared).
        // NO perspective for the longhand `rotate`: the web reference
        // folds the perspective property ONLY into the `transform`
        // function string (_dispatch.ts gates on a 3D function inside
        // it) — the browser renders `rotate: 1 1 0 45deg` next to a
        // `perspective:` declaration orthographically.
        if let t = c.translate { v = AnyView(applyFunction(t, to: v, anchor: anchor)) }
        if let r = c.rotate    { v = AnyView(applyFunction(r, to: v, anchor: anchor)) }
        if let s = c.scale     { v = AnyView(applyFunction(s, to: v, anchor: anchor)) }

        // Step 3 — `transform-style: preserve-3d` → wrap in drawingGroup
        // so SwiftUI rasterises the subtree in a single pass. This is
        // the closest analog to CSS's flatten-prevention semantics.
        if c.preserve3D { v = AnyView(v.drawingGroup()) }

        // Step 4 — BackfaceVisibility on the ACCUMULATED matrix (wave 19,
        // RC-B4a; css-transforms-2 §5.1): the element's own rotations
        // compose with the containing 3D rendering context's matrix, and
        // the plane is culled when the transformed normal's z component
        // goes negative (BackfaceCulling.backfaceZ — the WebKit cofactor
        // test). Replaces the old own-angles-only heuristic, which both
        // ignored ancestors AND mis-summed mixed X/Y rotations.
        if BackfaceCulling.isCulled(agg: c, inherited: inherited3D) {
            // Collapse to zero opacity but keep layout (SwiftUI has no
            // first-class backface-culling hook).
            v = AnyView(v.opacity(0))
        }

        // Step 4b — context propagation for DESCENDANTS: an element that
        // establishes/extends a 3D rendering context (perspective ≠ none,
        // or preserve-3d — css-transforms-2 §4) publishes the accumulated
        // matrix so nested faces cull against the full ancestor chain
        // (backface-visibility-hidden-001: container rotateY(45°) reaches
        // the grandchild faces through this channel). A touched FLAT
        // element instead RESETS the channel — its subtree is flattened
        // into its plane, ending the context. Untouched wrappers never
        // reach this body (the guard above) and pass the channel through,
        // a documented approximation that only forwards identity anyway.
        v = AnyView(v.environment(
            \.transforms3DAccumulated,
            BackfaceCulling.publishes3DContext(c)
                ? BackfaceCulling.accumulate(
                    own: BackfaceCulling.rotationMatrix(of: c),
                    inherited: inherited3D)
                : CATransform3DIdentity))

        // Step 5 — the `perspective` PROPERTY (css-transforms-2 §6).
        // Wave 5: consumed as the pendingPerspective SEED in Step 1
        // (web-reference convergence: the co-located length projects
        // the element's own 3D rotation). Beyond that seed the
        // longhand draws nothing here — applied to flat content the
        // §13.1 matrix is identity, and the wave-3 "depth-feel" scale
        // approximation (max(0.9, d/1000) when 3D-rotated) fabricated
        // a distortion neither web nor Android paints.
        // TransformsMath.perspectiveScale stays XCTest-pinned for the
        // record but the applier no longer draws with it.

        return v
    }

    // Dispatch one CSS transform function onto a view. We keep this out
    // of the main reduce loop to make the per-case SwiftUI API calls
    // legible (and so individual functions can log in the future).
    @ViewBuilder
    private func applyFunction(_ fn: TransformFn, to v: AnyView,
                               anchor: UnitPoint,
                               perspectivePx: CGFloat? = nil) -> some View {
        switch fn {
        case .translate(let x, let y, _, let xFrac, let yFrac):
            // `.offset(x:y:)` reproduces CSS translate exactly in 2D.
            // Z-component is dropped — SwiftUI has no Z-translate on a
            // non-3D view; documented limitation.
            // CSS percentage translates resolve against the element's
            // OWN border box (css-transforms-1 §6, transform-box on a
            // non-SVG element = border-box). Wave 5: the old
            // GeometryReader wrapper was GREEDY — it expanded to the
            // parent's proposal, so `translate: 50% 50%` on a 200×48 box
            // resolved 50% against the ~390px CANVAS width (the box flew
            // to the right edge with no visible Y shift). TranslateEffect
            // is a GeometryEffect like SkewEffect: SwiftUI hands it the
            // element's LAID-OUT size without letting it participate in
            // layout, so fractions resolve against the box itself.
            if xFrac != 0 || yFrac != 0 {
                v.modifier(TranslateEffect(x: x, y: y, xFrac: xFrac, yFrac: yFrac))
            } else {
                v.offset(x: x, y: y)
            }
        case .scale(let x, let y, _):
            // SwiftUI `.scaleEffect(x:y:anchor:)` honours the same
            // fractional anchor CSS uses, so anchor-aware scaling is
            // accurate.
            v.scaleEffect(x: x, y: y, anchor: anchor)
        case .rotate(let x, let y, let z, let deg):
            // Wave 5 — TRUE 3D rotation for axes with an X/Y component.
            // CSS renders a bare rotateY/rotateX ORTHOGRAPHICALLY (the
            // §13.1 matrix has no perspective row without a preceding
            // perspective(); the flattened box is a pure cos-scale), and
            // `perspective(d) rotateY(θ)` as a projective KEYSTONE.
            // SwiftUI's rotation3DEffect bakes in its own default
            // perspective (≠ CSS both ways) — the wave-5 3D cluster
            // (rotate axis-angle / perspective+rotateY, i-w 0.69–0.78).
            // Rotate3DEffect computes the CATransform3D exactly.
            if x != 0 || y != 0 {
                v.modifier(Rotate3DEffect(axisX: x, axisY: y, axisZ: z,
                                          deg: deg,
                                          perspectivePx: perspectivePx,
                                          anchor: anchor,
                                          anchorXPx: config?.origin?.xPx,
                                          anchorYPx: config?.origin?.yPx))
            } else {
                // Pure Z axis — plain 2D rotation, no projection at all.
                v.rotation3DEffect(.degrees(deg),
                                   axis: (x: x, y: y, z: z),
                                   anchor: anchor)
            }
        case .skew(let xDeg, let yDeg):
            // Wave 5: an explicit PX transform-origin (e.g. `30px 25%`)
            // rides the aggregate as origin.xPx/yPx — the fractional
            // UnitPoint stays at the 0.5 default for that axis, so the
            // shear anchored at the centre instead of the declared
            // origin (PW_Color_Transforms_01, skewY(20deg) at 30px:
            // ty = −tan20°·30 ≈ −10.9px, not −43.7px). SkewEffect
            // resolves the px override against the laid-out size.
            let pxX = config?.origin?.xPx
            let pxY = config?.origin?.yPx
            // SwiftUI has no first-class skew, and a bare
            // `.projectionEffect` applies its matrix about the view's
            // TOP-LEADING corner. CSS composes every transform function
            // about `transform-origin` (css-transforms-1 §8: translate
            // by origin · function · translate by −origin), default
            // 50% 50%. Shearing about the top edge instead of the
            // vertical centre displaced skewX(θ) content by a constant
            // +tan(θ)·h/2 — the wave-4 measured +4–5px rightward drift
            // on Performance_Decorated / Svg_Decorated (skewX(8deg),
            // tan 8° · 64/2 ≈ 4.5px) that web/Android never render
            // (Compose graphicsLayer pivots at centre by default).
            // SkewEffect is a GeometryEffect: it receives the laid-out
            // size without disturbing layout and conjugates the shear
            // about the anchor via TransformsMath.anchoredSkew.
            v.modifier(SkewEffect(xDeg: xDeg, yDeg: yDeg, anchor: anchor,
                                  anchorXPx: pxX, anchorYPx: pxY))
        case .matrix(let a, let b, let c, let d, let e, let f):
            // Direct 2D affine. CSS matrix(a,b,c,d,e,f) maps to
            // [[a,c,e],[b,d,f],[0,0,1]]; CGAffineTransform uses the
            // identical convention, so no adjustment needed.
            v.projectionEffect(ProjectionTransform(CGAffineTransform(
                a: a, b: b, c: c, d: d, tx: e, ty: f)))
        case .perspective(let distance):
            // Inline perspective() inside a transform list contributes
            // the §13.1 perspective matrix to the local accumulation.
            // Applied to flat (z = 0) content the matrix is EXACTLY
            // identity — w' = 1 − z/d = 1. Wave 5: the body loop
            // intercepts this case and folds the distance into the
            // FOLLOWING 3D rotation (Rotate3DEffect keystone), so this
            // branch only fires for a trailing/orphan perspective() —
            // identity by the spec math above.
            let _ = distance
            v
        }
    }

    // Wave 19: the old isBackFacing angle-sum heuristic was deleted —
    // the culling decision now lives in BackfaceCulling.isCulled (pure,
    // XCTest-pinned) so it can see the accumulated ancestor matrix.
}

extension View {
    // Chain helper — identity when config is nil or untouched.
    func engineTransforms(_ config: TransformsAggregate?) -> some View {
        modifier(TransformsApplier(config: config))
    }
}

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
