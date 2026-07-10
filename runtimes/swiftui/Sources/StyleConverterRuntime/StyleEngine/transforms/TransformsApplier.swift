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

struct TransformsApplier: ViewModifier {
    // Optional — nil means identity; the View extension below forwards
    // nil straight through, so there's no per-view cost on the common path.
    let config: TransformsAggregate?

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
        var v: AnyView = AnyView(content)
        for fn in c.functions {
            v = AnyView(applyFunction(fn, to: v, anchor: anchor))
        }

        // Step 2 — longhand overrides, in CSS spec order: translate,
        // rotate, scale. Any of the three may be nil (not declared).
        if let t = c.translate { v = AnyView(applyFunction(t, to: v, anchor: anchor)) }
        if let r = c.rotate    { v = AnyView(applyFunction(r, to: v, anchor: anchor)) }
        if let s = c.scale     { v = AnyView(applyFunction(s, to: v, anchor: anchor)) }

        // Step 3 — `transform-style: preserve-3d` → wrap in drawingGroup
        // so SwiftUI rasterises the subtree in a single pass. This is
        // the closest analog to CSS's flatten-prevention semantics.
        if c.preserve3D { v = AnyView(v.drawingGroup()) }

        // Step 4 — BackfaceVisibility: hide when the cumulative Y/X
        // rotation sends us past 90° (best-effort; SwiftUI lacks a
        // first-class backface-culling hook).
        if c.backfaceHidden && isBackFacing(c) {
            // Collapse to zero opacity but keep layout.
            v = AnyView(v.opacity(0))
        }

        // Step 5 — perspective (css-transforms-2 §6). Two regimes,
        // split by whether the element is actually 3D-rotated
        // (TransformsMath.has3DRotation — any rotate with an X/Y axis
        // component in the function list or longhand):
        //   • FLAT content → identity. The projection of the z = 0
        //     plane is exactly (x, y, 0, 1) — w' = 1 − 0/d = 1 (§13.1
        //     matrix) — so with nothing rotated out of plane, nothing
        //     may change. The previous unconditional "foreshorten by
        //     max(0.9, d/1000)" shrank + inset flat components by up
        //     to 10% — the fabricated distortion the wave-3
        //     measurement flagged (Transforms_TextBlock 0.757 /
        //     Transforms_Decorated 0.816; the inverse scaling with
        //     distance confirmed the mechanism). Web/Android render
        //     these byte-flat.
        //   • 3D-rotated element → keep the legacy foreshortening
        //     approximation. SwiftUI has no true perspective divide on
        //     a 2D view chain, and the committed 046_Perspective_Rotate
        //     baseline (perspective: 500px + rotateY(30deg)) pins this
        //     depth-feel approximation — verified by the BASELINE=1
        //     visual-test run.
        if let p = c.perspective, let d = p.distancePx {
            let k = TransformsMath.perspectiveScale(
                distancePx: CGFloat(d),
                has3DRotation: TransformsMath.has3DRotation(c))
            if k != 1 { v = AnyView(v.scaleEffect(k, anchor: p.origin)) }
        }

        return v
    }

    // Dispatch one CSS transform function onto a view. We keep this out
    // of the main reduce loop to make the per-case SwiftUI API calls
    // legible (and so individual functions can log in the future).
    @ViewBuilder
    private func applyFunction(_ fn: TransformFn, to v: AnyView,
                               anchor: UnitPoint) -> some View {
        switch fn {
        case .translate(let x, let y, _, let xFrac, let yFrac):
            // `.offset(x:y:)` reproduces CSS translate exactly in 2D.
            // Z-component is dropped — SwiftUI has no Z-translate on a
            // non-3D view; documented limitation.
            // CSS percentage translates resolve against the element's
            // own size (CSS Transforms 2 §3.2). When either fraction is
            // non-zero we wrap in a GeometryReader so the offset can mix
            // absolute pts with width/height-relative pts.
            if xFrac != 0 || yFrac != 0 {
                GeometryReader { geo in
                    v.offset(x: x + geo.size.width * xFrac,
                             y: y + geo.size.height * yFrac)
                        .frame(width: geo.size.width, height: geo.size.height)
                }
            } else {
                v.offset(x: x, y: y)
            }
        case .scale(let x, let y, _):
            // SwiftUI `.scaleEffect(x:y:anchor:)` honours the same
            // fractional anchor CSS uses, so anchor-aware scaling is
            // accurate.
            v.scaleEffect(x: x, y: y, anchor: anchor)
        case .rotate(let x, let y, let z, let deg):
            // `.rotation3DEffect` collapses to 2D rotate when axis is
            // (0,0,z), which is what CSS `rotate(deg)` actually means.
            v.rotation3DEffect(.degrees(deg),
                               axis: (x: x, y: y, z: z),
                               anchor: anchor)
        case .skew(let xDeg, let yDeg):
            // SwiftUI has no first-class skew. Use ProjectionEffect with
            // a CGAffineTransform encoding the two skew tangents.
            let tx = tan(xDeg * .pi / 180)
            let ty = tan(yDeg * .pi / 180)
            v.projectionEffect(ProjectionTransform(CGAffineTransform(
                a: 1, b: ty, c: tx, d: 1, tx: 0, ty: 0)))
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
            // identity — w' = 1 − z/d = 1 — so with no 3D function in
            // the list there is nothing to draw differently (same spec
            // math as the longhand in Step 5; the old unconditional
            // shrink fabricated up to 10% distortion). The 3D gate is
            // threaded from the aggregate by the caller via
            // `config`; recompute here for the same regime split.
            v.scaleEffect(TransformsMath.perspectiveScale(
                              distancePx: distance,
                              has3DRotation: config.map(TransformsMath.has3DRotation) ?? false),
                          anchor: anchor)
        }
    }

    // Heuristic backface test — sums Y/X rotation degrees from the
    // function list + longhand and returns true if the running angle
    // lands in the (90°, 270°) range mod 360°.
    private func isBackFacing(_ agg: TransformsAggregate) -> Bool {
        // Collect every rotate contribution, including the longhand.
        var total: CGFloat = 0
        let all = agg.functions + [agg.rotate].compactMap { $0 }
        for fn in all {
            if case .rotate(_, let y, _, let d) = fn, y != 0 { total += d }
            if case .rotate(let x, _, _, let d) = fn, x != 0 { total += d }
        }
        // Normalise to [0, 360) then check "back half".
        let norm = ((total.truncatingRemainder(dividingBy: 360)) + 360)
            .truncatingRemainder(dividingBy: 360)
        return norm > 90 && norm < 270
    }
}

extension View {
    // Chain helper — identity when config is nil or untouched.
    func engineTransforms(_ config: TransformsAggregate?) -> some View {
        modifier(TransformsApplier(config: config))
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
