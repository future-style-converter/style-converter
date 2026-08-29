//
//  FilterApplier.swift
//  StyleEngine/effects/filter — Phase 8.
//
//  Folds a `FilterConfig` into a SwiftUI modifier chain. CSS filters
//  compose left-to-right — each function sees the output of the
//  previous one — so we reduce in declared order. Equivalent SwiftUI
//  APIs:
//
//    blur(sigma)        → .blur(radius: sigma)   §8.2: the CSS
//                         parameter IS σ, and SwiftUI's radius ≈ σ
//    brightness(pct)    → .colorMultiply(white: pct/100)   MULTIPLIER, not additive
//    contrast(pct)      → .contrast(pct/100)
//    grayscale(pct)     → .grayscale(pct/100)
//    saturate(pct)      → .saturation(pct/100)
//    hue-rotate(deg)    → .hueRotation(.degrees(deg))
//    invert(a)          → lerp to .colorInvert() at opacity a   §8.6
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
            // filter-effects-1 §8.2: the parameter of blur() IS the Gaussian
            // STANDARD DEVIATION — "the parameter defines the value of the
            // standard deviation to the Gaussian function". It is NOT a
            // radius, and it is NOT the box-shadow/drop-shadow convention
            // (css-backgrounds-3, filter-effects-1 §10.1) where a blur
            // radius r means σ = r/2. Conflating the two is the bug this
            // line had: it halved σ on every blur.
            //
            // MEASURED with the same step-edge estimator BackdropBlur pins
            // `ciRadiusPerSigma` with (the derivative of a blurred step edge
            // IS the Gaussian, so its second moment is σ). Across four
            // radii, iOS/web σ ratio was a constant ~0.48:
            //
            //     blur(R)     R=1     R=2     R=4     R=8
            //     web σ      1.080   1.991   3.835   6.815   (≈ R, correct)
            //     iOS σ      0.455   0.954   1.845   3.624   (≈ R/2)
            //
            // The constant ratio is the signature of a scale error, and it
            // also tells us SwiftUI's `.blur(radius:)` parameter is itself
            // ≈ σ — passing R/2 produced σ ≈ R/2 at every radius. So the
            // correct call passes R unchanged.
            v.blur(radius: r, opaque: false)
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
            // COMPOSITING: plain source-over, and the reason is measured
            // rather than assumed — the "more correct" alternative was tried
            // and reverted.
            //
            // Source-over of the sepia layer at .opacity(a) gives exactly
            // a*s + (1-a)*c when the content is OPAQUE, which is every case
            // in the fixture suite bar one. It is wrong when the content's
            // own alpha < 1, because .opacity(a) multiplies the top layer's
            // alpha: coverage A comes out as A*a + (1-A*a)*A, not A.
            //
            // The textbook fix is complementary opacities with .plusLighter
            // inside a .compositingGroup (the idiom CrossFadeApplier uses).
            // It fixes the translucent case and BREAKS SOMETHING WORSE:
            // SwiftUI Text stops being filtered at all. Measured on white
            // text under sepia(80%), where the spec and both other runtimes
            // give (255,255,242):
            //
            //     construction   opaque box   text          translucent bg
            //     src-over       7/8 EXACT    (255,255,242) dist 42
            //     plusLighter    1 LSB off    (255,255,255) dist 1
            //                                  ^ unfiltered
            //
            // Text inside a filtered element is far more common than a
            // translucent filtered background, and src-over also recovers
            // the green LSB on the solid box (158 vs 157, matching Android
            // and web exactly). So src-over wins on the evidence.
            //
            // The translucent case is therefore a KNOWN iOS divergence,
            // carried in cross-platform-expectations.json rather than
            // silently traded away. Fixing both needs a compositing route
            // that preserves alpha WITHOUT flattening text — no SwiftUI
            // primitive combination found so far does that.
            let sepiaAmount = SepiaMatrix.amount(pct)
            if sepiaAmount <= 0 {
                // amount 0 is the identity — no stack, no second render.
                v
            } else {
                ZStack {
                    // The (1-a)*c term: the unfiltered element.
                    v
                    // The a*(w.c)*t term, composited over it at the amount.
                    v.colorMultiply(SepiaMatrix.reweightColor)  // -> sepia's basis
                        .grayscale(1.0)                          // -> w.c, broadcast
                        .colorMultiply(SepiaMatrix.tintColor)    // -> x t
                        .opacity(sepiaAmount)
                }
            }
        case .invert(let pct):
            // filter-effects-1 §8.6: invert(a) is a per-channel LINEAR
            // transfer — feFuncR type="table" tableValues="a 1-a" — i.e.
            //
            //     out = (1-a)·c + a·(1-c)
            //
            // exact at a=0 and a=1 and a straight lerp between. It is NOT a
            // threshold.
            //
            // The old code was `if pct >= 50 { v.colorInvert() } else { v }`,
            // which snapped to whichever endpoint was nearer, and its comment
            // ("we only invert at 100%") did not even describe that. Measured
            // on the committed fixture fixtures/properties/color/filter.json,
            // component F_invert = #3b82f6 (59,130,246) under invert(0.5):
            //
            //     spec / web / Android   (128,128,128)   flat mid-grey
            //     old iOS                (196,125, 9)    a FULL inversion
            //
            // RGB distance 137 — larger than the 97 that condemned sepia.
            // The discontinuity cut both ways: invert(49%) rendered the
            // original untouched.
            //
            // This runtime already had the correct formula: BackdropImageOps
            // .invertByte computes `c + (255 - 2c)·amount` with this same
            // §8.6 citation, for the BACKDROP path. Only the foreground
            // filter lane was missing it.
            //
            // Composited src-over at .opacity(a), matching the sepia case in
            // this file — see its comment for why source-over rather than an
            // additive stack (the additive form stops SwiftUI Text being
            // filtered at all). The alpha caveat is identical and shared.
            let invertAmount = SepiaMatrix.amount(pct)   // reuse: clamp pct/100 to 0…1
            if invertAmount <= 0 {
                v
            } else if invertAmount >= 1 {
                // Full inversion needs no second layer.
                v.colorInvert()
            } else {
                ZStack {
                    v
                    v.colorInvert().opacity(invertAmount)
                }
            }
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
