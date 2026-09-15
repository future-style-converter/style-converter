//
//  BlendModeApplier.swift
//  StyleEngine/effects/blend — Phase 4.
//
//  Applies the `mix` half of a BlendModeConfig via SwiftUI's
//  `.blendMode(_:)` — mix-blend-mode acts on the whole element against
//  its backdrop, which is exactly what the modifier does. The
//  `background` half (background-blend-mode) is NOT handled here: since
//  the IOS-BBM lane it is consumed inside the background chain by
//  BackgroundImageApplier + BackgroundBlendCompositor (per-layer
//  .blendMode inside an isolated .compositingGroup — CSS Compositing 1
//  §3.2), so this applier is deliberately identity for background-only
//  configs.
//

import SwiftUI

struct BlendModeApplier: ViewModifier {
    let config: BlendModeConfig?

    func body(content: Content) -> some View {
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }

        // `mix-blend-mode` blends the entire ELEMENT — as one finished
        // group — against its backdrop (compositing-1 §5.1: a non-normal
        // `mix-blend-mode` makes the element a stacking context, so its own
        // background, borders, text and in-flow descendants composite
        // together into a group first — compositing-1 §3.1 — and only that
        // group is then blended with what is painted below it).
        //
        // SwiftUI's `.blendMode(_:)` does NOT do that on its own: it sets a
        // blend ATTRIBUTE that propagates down to every leaf drawing
        // operation of the modified view, so each primitive blends against
        // whatever is already in the destination — including the element's
        // OWN earlier primitives. `.compositingGroup()` is the documented
        // way to flatten the subtree into one offscreen buffer first, so
        // the attribute lands on that single buffer and the leaves inside
        // it composite normally against each other. The pair is exactly the
        // §3.1 group + §5.1 blend split.
        //
        // MEASURED (wave 50, post-gate iOS fix lane, Catalyst ImageRenderer
        // at scale 1 on a byte-mirror of the harness CaptureCanvas — proven
        // byte-identical to the simulator captures it predicts):
        // a `mix-blend-mode: multiply` box over #ffcc00 painted its own
        // label glyphs multiplied AGAINST ITS OWN BACKGROUND RECT instead of
        // once, as part of the group:
        //
        //   fixtures/combinations/opacity-blend.json OB_Multiply_NoOpacity
        //     iOS (97,155,0) · web/Android (196,182,0)   75 glyph px
        //   fixtures/combinations/blend-isolation.json 003_wrapper
        //     iOS (97,193,242) · web/Android (197,227,242)  75 glyph px
        //     — 0.60% of that 390×32 crop, which is the SSIM 0.9492 row
        //       the wave-50 gate failed on (Δpx 0.60%, ΔE95 0.00: the flat
        //       fill already agreed, only the glyph ink did not)
        //   fixtures/visual-test.json BlendMode_Multiply (087)
        //     iOS (23,8,10) · web (24,19,33)             59 glyph px
        //
        // The flat fills are unaffected — a solid rectangle has nothing of
        // its own underneath it, so grouping cannot change its blend — which
        // is why every *_Op50 row and both NoOpacity fills stay byte-identical
        // and PR #126's opacity×blend ordering (StyleBuilder: isolation →
        // opacity → blend, blend OUTERMOST) is untouched: this group is
        // inside `.blendMode` and outside the opacity group, exactly where
        // §3.1 puts it.
        if let m = cfg.mix {
            return AnyView(content.compositingGroup().blendMode(m))
        }

        // `background-blend-mode` is a different beast: per CSS
        // Compositing 1 §3.2 it composites background LAYERS within the
        // element's background painting area, not the element against
        // its parent — a historical fallback here applied the first
        // value as a whole-view `.blendMode`, which multiplied red
        // against the dark canvas into near-black (the Phase-12
        // BlendModeMultiply audit regression). IOS-BBM: the real
        // compositor now lives in the background chain
        // (BackgroundImageApplier routes non-normal mode lists through
        // BackgroundBlendCompositor's isolated ZStack; StyleBuilder
        // threads the modes via activeBackgroundBlendModes), so a
        // background-only config is correctly IDENTITY at this level.
        return AnyView(content)
    }
}

extension View {
    // Chain helper. Use once in applyStyle.
    func engineBlendMode(_ config: BlendModeConfig?) -> some View {
        modifier(BlendModeApplier(config: config))
    }
}
