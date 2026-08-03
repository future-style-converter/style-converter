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
//    brightness(pct)    → .brightness((pct-100)/100)
//    contrast(pct)      → .contrast(pct/100)
//    grayscale(pct)     → .grayscale(pct/100)
//    saturate(pct)      → .saturation(pct/100)
//    hue-rotate(deg)    → .hueRotation(.degrees(deg))
//    invert(100)        → .colorInvert()        (partial invert is lossy)
//    opacity(pct)       → .opacity(pct/100)
//    sepia              → approximate with .saturation + hue shift (no native API)
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

    // One filter function → one SwiftUI modifier.
    @ViewBuilder
    private func applyOne(_ fn: FilterFn, to v: AnyView) -> some View {
        switch fn {
        case .blur(let r):
            // CSS blur radius ≈ 2× SwiftUI's internal radius; divide by 2.
            v.blur(radius: r / 2, opaque: false)
        case .brightness(let pct):
            // SwiftUI `.brightness` is additive in [-1,1]; CSS is
            // multiplicative on the colour (0 ≡ black, 100 ≡ identity).
            // Map by `(pct-100)/100` so 100 → 0, 150 → +0.5, 50 → -0.5.
            v.brightness((pct - 100) / 100)
        case .contrast(let pct):
            v.contrast(pct / 100)
        case .grayscale(let pct):
            // CSS grayscale amount in 0–100; SwiftUI expects 0–1.
            v.grayscale(pct / 100)
        case .sepia(let pct):
            // No native sepia filter. Approximate by desaturating and
            // tinting with a warm overlay. Amount in 0–1.
            v.saturation(1 - pct / 200)
                .colorMultiply(Color(red: 1.0, green: 0.9, blue: 0.7)
                               .opacity(pct / 100))
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
