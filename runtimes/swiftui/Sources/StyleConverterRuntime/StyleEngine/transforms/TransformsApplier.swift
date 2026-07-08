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

        // Step 5 — perspective (projection). SwiftUI's ProjectionEffect
        // applies a CGAffineTransform so pure perspective isn't
        // expressible; we approximate via a small foreshortening scale
        // when a finite distance was declared. Documented TODO.
        if let p = c.perspective, let d = p.distancePx, d > 0 {
            // Approximate "depth feel" — a 1000px distance maps to full
            // size; smaller distance → slight shrink. Max 10% so layout
            // tests don't drift wildly off-baseline.
            let k = max(0.9, min(1.0, CGFloat(d) / 1000.0))
            v = AnyView(v.scaleEffect(k, anchor: p.origin))
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
            // Inline perspective() inside a transform list — shrink to
            // simulate viewing distance, same approximation as the
            // longhand path.
            let k = distance > 0 ? max(0.9, min(1.0, distance / 1000.0)) : 1.0
            v.scaleEffect(k, anchor: anchor)
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
