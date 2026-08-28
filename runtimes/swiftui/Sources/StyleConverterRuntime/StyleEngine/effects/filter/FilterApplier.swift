//
//  FilterApplier.swift
//  StyleEngine/effects/filter — Phase 8.
//
//  Folds a `FilterConfig` into a SwiftUI modifier chain. CSS filters
//  compose left-to-right — each function sees the output of the
//  previous one — so we reduce in declared order. Equivalent SwiftUI
//  APIs:
//
//    blur(radius)       → .blur(radius: CGFloat × 0.5 for CSS parity)
//    brightness(pct)    → .colorMultiply(white: pct/100)   MULTIPLIER, not additive
//    contrast(pct)      → .contrast(pct/100)
//    grayscale(pct)     → .grayscale(pct/100)
//    saturate(pct)      → .saturation(pct/100)
//    hue-rotate(deg)    → .hueRotation(.degrees(deg))
//    invert(100)        → .colorInvert()        (partial invert is lossy)
//    opacity(pct)       → .opacity(pct/100)
//    sepia(pct)         → §8.5 matrix to within a quantisation step, via
//                         SepiaMatrix's rank-1 factorisation (reweight →
//                         .grayscale → tint, additively blended)
//    drop-shadow        → .shadow(color:, radius:, x:, y:)
//
//  BackdropFilter has no first-class SwiftUI API. Since lane BF-I it is
//  rendered by the TWO-PASS capture in StyleEngine/effects/backdrop (see
//  BackdropPass.swift for the passes and the backdrop-root boundary);
//  `cfg.backdrop` — untouched here since Phase 8 — is that lane's
//  registration point, in step 2 of body() below. It is attached OUTSIDE
//  the foreground chain on purpose; see the comment there.
//

import SwiftUI

struct FilterApplier: ViewModifier {
    let config: FilterConfig?
    /// The element's border radius, forwarded to the backdrop applier so
    /// the backplate is clipped to the same rounded border box as the
    /// element's own background. Defaults to nil so the only non-engine
    /// caller shape (`engineFilter(_:)` without a radius) keeps compiling
    /// and keeps square-box behaviour.
    var radius: BorderRadiusConfig? = nil
    /// The element's CSS `opacity` PROPERTY (not the filter function).
    /// Wave 26 skeptic P8: StyleBuilder attaches `.engineOpacity` at 1108,
    /// INNER of this modifier (1131), so the backplate `.background` escapes
    /// it — an `opacity: 0` element painted a full-strength backplate where
    /// the browser ref (backdrop-filter-basic-opacity) paints nothing. The
    /// Compose twin threads `elementAlpha` for the same reason; the backplate
    /// must fade with the element it belongs to.
    var elementOpacity: Double = 1

    func body(content: Content) -> some View {
        // Short-circuit — nil / untouched means identity.
        guard let cfg = config, cfg.touched else { return AnyView(content) }

        // Step 1: foreground filter chain — the element's OWN pixels.
        // Iterate in declared order so the visual effect of
        // `blur(4) brightness(120)` differs from `brightness(120) blur(4)` —
        // SwiftUI modifier order matches CSS function order here (outermost
        // applied last).
        var v: AnyView = AnyView(content)
        for fn in cfg.filter {
            v = AnyView(applyOne(fn, to: v))
        }

        // Step 2: backdrop filter, attached AFTER (hence OUTSIDE) the
        // foreground chain. SwiftUI has no first-class backdrop API (web
        // emits real CSS `backdrop-filter`; Android has RenderEffect on API
        // 31+). Phase 8 shipped `.thinMaterial` here, which painted a grey
        // rectangle even with nothing behind the element, and that was then
        // removed — leaving iOS a documented no-op.
        //
        // Lane BF-I replaces the no-op with the two-pass capture: pass A
        // renders the canvas with this element's paint suppressed, pass B
        // crops that raster to this element's border box, filters it, and
        // draws it underneath. The modifier is IDENTITY unless a capture
        // path publishes `backdropPass` (default `.disabled`), so every
        // committed baseline and every SDUI app render is unchanged.
        //
        // ORDER IS LOAD-BEARING (wave-26 skeptic fix #3). This modifier was
        // attached BEFORE the loop above, so the foreground chain wrapped the
        // backplate: `backdrop-filter: invert(1)` plus `filter: invert(1)`
        // inverted the sampled backdrop a SECOND time and landed back on the
        // unfiltered colour — an executed double-apply, not a theoretical
        // one. filter-effects-2 §2 draws the filtered backdrop under the
        // element's paint and outside the element's own `filter`, which is
        // what attaching it out here gives (`.background` paints beneath the
        // already-filtered content, and no foreground modifier is outer of
        // it). Pass A is unaffected: `.opacity(0)` out here still suppresses
        // the whole filtered subtree.
        if !cfg.backdrop.isEmpty {
            // Plan first (pure): the invert/blur subset this lane executes —
            // ALL-OR-NOTHING, so a chain carrying anything else plans to
            // identity and the applier logs the refusal once.
            let plan = BackdropPlan.plan(from: cfg.backdrop)
            // opacity <= 0: the element contributes nothing — mirror the
            // Compose twin's early-out (no backplate, no pass-A cost).
            if elementOpacity > 0 {
                v = AnyView(v.modifier(BackdropApplier(plan: plan, radius: radius, elementOpacity: elementOpacity)))
            }
        }
        return v
    }

    /// CSS `brightness(pct)` -> the multiplicative factor `.colorMultiply`
    /// needs. Split out of `applyOne` purely so the decision that was wrong
    /// is unit-testable without rendering: the old code computed
    /// `(pct - 100) / 100`, which is SwiftUI's ADDITIVE brightness parameter,
    /// not the CSS multiplier. See FilterBrightnessTests.
    ///
    /// filter-effects-1 gives no upper bound, so values above 1 are returned
    /// as-is and ride an extended-range colour; the sRGB capture context
    /// clamps at the end, which is where CSS clamps too. Negative amounts are
    /// invalid CSS and clamp to 0 (black) rather than inverting the image.
    static func brightnessFactor(_ pct: Double) -> Double { max(0, pct / 100) }

    // One filter function → one SwiftUI modifier.
    @ViewBuilder
    private func applyOne(_ fn: FilterFn, to v: AnyView) -> some View {
        switch fn {
        case .blur(let r):
            // CSS blur radius ≈ 2× SwiftUI's internal radius; divide by 2.
            v.blur(radius: r / 2, opaque: false)
        case .brightness(let pct):
            // filter-effects-1 §2.2: brightness() is a linear MULTIPLIER on
            // the colour channels — 100 is identity, 150 scales each channel
            // by 1.5, 0 is black. SwiftUI's `.brightness(_:)` is an ADDITIVE
            // shift in [-1, 1]: a different operation that only coincides
            // with CSS at the identity point.
            //
            // The old mapping `(pct - 100) / 100` implemented the additive
            // one. Measured on Filter_Brightness (#2ecc71 = (46,204,113),
            // brightness(150)) across the three runtimes:
            //
            //   multiplicative  (46,204,113) x 1.5      -> (69, 255, 170)
            //   additive        (46,204,113) + 0.5*255  -> (174, 255, 240)
            //
            //   web      (69, 255, 170)   correct
            //   Android  (69, 255, 170)   correct
            //   iOS      (174, 255, 241)  the additive result
            //
            // `.colorMultiply` is the multiplicative operator (already used
            // by the sepia case below). Amounts above 100 need a colour
            // component greater than 1, which sRGB carries as extended
            // range; the capture context clamps to [0,1] afterwards, and
            // clamping at the END is what CSS specifies — brightness(150)
            // on a channel already at 204 is meant to saturate at 255.
            //
            // Negative amounts are invalid CSS; clamp at 0 (black) rather
            // than letting a negative multiplier invert the image.
            let factor = FilterApplier.brightnessFactor(pct)
            v.colorMultiply(Color(.sRGB, red: factor, green: factor, blue: factor, opacity: 1))
        case .contrast(let pct):
            v.contrast(pct / 100)
        case .grayscale(let pct):
            // CSS grayscale amount in 0–100; SwiftUI expects 0–1.
            v.grayscale(pct / 100)
        case .sepia(let pct):
            // filter-effects-1 §8.5's colour matrix, via the least-squares
            // rank-1 factorisation in SepiaMatrix (see that file for the
            // derivation, the platform facts it rests on, and why a real
            // colour matrix was declined):
            //
            //     M(a)·c ≈ (1-a)·c + a·(w·c)·t
            //
            // `.grayscale(1)` sums with Rec.709 weights, so a per-channel
            // REWEIGHT first makes it sum in sepia's basis instead.
            //
            // The two layers are composited the way CrossFadeApplier does
            // it, and for the same reason: COMPLEMENTARY OPACITIES plus
            // `.plusLighter` (component-wise premultiplied ADD) inside a
            // `.compositingGroup()`.
            //
            // The obvious construction — draw the sepia layer over the
            // original at `.opacity(a)` with ordinary source-over — is
            // WRONG for anything not fully opaque, and this file had it
            // that way first. Source-over multiplies the top layer's alpha,
            // so for coverage α the result carries α·a + (1-α·a)·α, not α.
            // Computed for `rgba(52,152,219,0.5)` under `sepia(80%)` over
            // the #1a1a2e page:
            //
            //     spec / web / Android   (90, 92, 95)   out_alpha 0.50
            //     src-over ZStack        (95,117,129)   out_alpha 0.70
            //     this construction      (90, 92, 95)   out_alpha 0.50
            //
            // That is every translucent background, every `opacity` on a
            // filtered element, and every antialiased edge — CrossFadeApplier
            // already documents the identical trap for weighted image
            // layers ("sequential src-over stacking would give 1−0.9⁶").
            //
            // Additive layers sum to the lerp with alpha untouched:
            //     (1-a)·α·c + a·α·s   carries alpha (1-a)·α + a·α = α.
            let sepiaAmount = SepiaMatrix.amount(pct)
            if sepiaAmount <= 0 {
                // amount 0 is the identity — no group, no second render.
                v
            } else {
                ZStack {
                    // The (1-a)·c term, pre-scaled for the additive sum.
                    v.opacity(1 - sepiaAmount)
                        .blendMode(.plusLighter)
                    // The a·(w·c)·t term.
                    v.colorMultiply(SepiaMatrix.reweightColor)  // → sepia's basis
                        .grayscale(1.0)                          // → w·c, broadcast
                        .colorMultiply(SepiaMatrix.tintColor)    // → × t
                        .opacity(sepiaAmount)
                        .blendMode(.plusLighter)
                }
                // Isolate: without the group, plusLighter would add into
                // whatever the page already painted below this element.
                .compositingGroup()
            }
        case .invert(let pct):
            // SwiftUI has a boolean colorInvert; we only invert at 100%.
            if pct >= 50 { v.colorInvert() } else { v }
        case .saturate(let pct):
            v.saturation(pct / 100)
        case .opacity(let pct):
            v.opacity(pct / 100)
        case .hueRotate(let deg):
            v.hueRotation(.degrees(deg))
        case .dropShadow(let x, let y, let blur, let color):
            // SwiftUI `.shadow` has the same semantics as CSS drop-shadow:
            // it respects the alpha channel of the view so transparent
            // pixels don't cast shadow. Perfect parity.
            v.shadow(color: color ?? .black.opacity(0.3),
                     radius: blur / 2, x: x, y: y)
        case .url(let id):
            // Can't resolve SVG filter refs in SwiftUI — log-and-skip.
            // TODO: fold a pre-rendered SVG filter into a shader.
            let _ = id
            v
        }
    }
}

extension View {
    // Chain helper; identity on nil. `radius` is the element's border
    // radius — used ONLY to clip the lane BF-I backdrop backplate to the
    // rounded border box; it does not affect the foreground filter chain,
    // and defaults to nil (square) for callers that have no radius in hand.
    func engineFilter(_ config: FilterConfig?,
                      radius: BorderRadiusConfig? = nil,
                      elementOpacity: Double = 1) -> some View {
        modifier(FilterApplier(config: config, radius: radius,
                               elementOpacity: elementOpacity))
    }
}
