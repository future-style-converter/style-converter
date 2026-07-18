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

        // `mix-blend-mode` blends the entire element against its
        // siblings / parents — exactly what `.blendMode(_:)` does in
        // SwiftUI. We honour it directly.
        if let m = cfg.mix {
            return AnyView(content.blendMode(m))
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
