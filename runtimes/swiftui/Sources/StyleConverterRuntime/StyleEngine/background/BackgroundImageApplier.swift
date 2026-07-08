//
//  BackgroundImageApplier.swift
//  StyleEngine/background — Phase 4.
//
//  Stacks an ordered BackgroundImage layer list behind the content. CSS
//  layer order: index 0 paints on TOP of index 1 paints on TOP of ...
//  We reverse the array when chaining `.background(...)` because each
//  successive `.background(...)` in SwiftUI sits BELOW the prior one,
//  giving us the same effective top-of-stack order.
//

import SwiftUI

struct BackgroundImageApplier: ViewModifier {
    // Nil = no BackgroundImage property → identity.
    let config: BackgroundImageConfig?

    func body(content: Content) -> some View {
        // Short-circuit when nothing to paint.
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }

        // SwiftUI stacks `.background(X).background(Y)` with X on top of
        // Y. CSS stacks layers[0] on top of layers[1]. So we walk the
        // layers in source order (0..N) attaching `.background` for each,
        // which gives the correct z-ordering naturally.
        var view: AnyView = AnyView(content)
        for layer in cfg.layers {
            // Render each layer then attach as background. Preserves
            // stacking even for `.none` layers (painted as Clear).
            let rendered = GradientApplier.render(layer)
            view = AnyView(view.background(rendered))
        }
        return view
    }
}

extension View {
    // Chain helper — invoked once per ComponentStyle from StyleBuilder.
    func engineBackgroundImage(_ config: BackgroundImageConfig?) -> some View {
        modifier(BackgroundImageApplier(config: config))
    }
}
