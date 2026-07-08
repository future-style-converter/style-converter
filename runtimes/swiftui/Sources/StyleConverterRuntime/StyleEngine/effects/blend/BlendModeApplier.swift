//
//  BlendModeApplier.swift
//  StyleEngine/effects/blend — Phase 4.
//
//  Applies a BlendModeConfig via SwiftUI's `.blendMode(_:)`. The applier
//  prefers `mix` (per the CSS cascade intent — mix-blend-mode acts on
//  the whole element). When `mix` is nil but `background` has entries,
//  we use the first background blend mode on the whole view, since
//  SwiftUI doesn't expose per-layer blending. Documented limitation.
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
        // its parent. The previous fallback applied the first
        // `background-blend-mode` value as a whole-view `.blendMode`,
        // which darkened the element against the dark canvas backdrop —
        // `red × #1A1A2E` collapses near-black for `multiply`, matching
        // the Phase-12 BlendModeMultiply audit regression where iOS
        // rendered almost solid black. SwiftUI doesn't expose
        // per-layer-within-background blending, so until a dedicated
        // background compositor lands here we render the gradients
        // unblended. That's still not visually identical to web (which
        // composites the layers), but it's much closer than the wrong
        // whole-view multiply against the canvas.
        return AnyView(content)
    }
}

extension View {
    // Chain helper. Use once in applyStyle.
    func engineBlendMode(_ config: BlendModeConfig?) -> some View {
        modifier(BlendModeApplier(config: config))
    }
}
