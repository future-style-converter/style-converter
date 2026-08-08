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

        // Step 2b — NON-INVERTIBLE used transform (wave 35, lane B1).
        // css-transforms-1 §3: "If the transform is not invertible, the
        // element and its content are not rendered." A rotateX/rotateY of
        // exactly ±90° collapses the box edge-on, and SwiftUI does NOT
        // collapse with it: `ProjectionTransform` with a zero-determinant
        // slice falls back to IDENTITY, so Rotate3DEffect drew the box
        // UNROTATED at full size (3d-rendering-context-and-inline rendered a
        // solid red 100×100 square where web and Android render blank —
        // i-ref 0.9554). Hiding it here reproduces the browser: opacity 0
        // keeps the box in layout (§3 removes it from PAINT, not from flow)
        // and reuses the exact channel Step 4's backface culling uses.
        // SingularTransform carries the pure predicate, its numeric proof
        // that transform-origin cannot change the answer, and the measured
        // two-box blast radius over the frozen corpus.
        if SingularTransform.isSingular(c) { v = AnyView(v.opacity(0)) }

        // Step 3 — `transform-style: preserve-3d` emits NO modifier here
        // (wave 20, B-RC4). The old `.drawingGroup()` wrap was backwards
        // twice over: (a) drawingGroup FLATTENS the subtree into one
        // offscreen raster — the exact flattening css-transforms-2 §4
        // says preserve-3d must prevent — and (b) it CLIPS that raster
        // to the view's bounds, while CSS never clips at a preserve-3d
        // boundary (overflow is a separate, default-visible property).
        // (b) blanked backface-visibility-hidden-001's WHOLE 3D subtree:
        // the preserve-3d DIV holds only abspos faces (rendered via the
        // renderer's `.overlay` channel), so its auto height is 0 and
        // the offscreen pass rasterised a 200×0 — i.e. empty — texture
        // (raster-pinned by Backface3DSubtreeRasterTests, probes C/E).
        // preserve-3d is still consumed, not dropped: Step 4b below
        // publishes the accumulated 3D context it establishes, and the
        // ancestor's own projective GeometryEffect (Rotate3DEffect)
        // already warps this subtree because SwiftUI modifiers wrap the
        // fully-composed content, overlays included.

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
