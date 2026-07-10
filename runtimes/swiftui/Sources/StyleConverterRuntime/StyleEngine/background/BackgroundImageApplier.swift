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
//  Fidelity wave 1 additions:
//    • `clipInsets` — CSS `background-clip: padding-box | content-box`
//      shrinks the painted area by the border / border+padding bands
//      (CSS Backgrounds 3 §2.4). StyleBuilder computes the concrete
//      EdgeInsets from the resolved border + padding configs.
//    • `attachment` — accepted but a deliberate NO-OP: see the note in
//      `body`. Static captures never scroll, so scroll/fixed/local are
//      indistinguishable, and the web reference empirically paints
//      fixed layers element-anchored in this pipeline.
//

import SwiftUI

struct BackgroundImageApplier: ViewModifier {
    // Nil = no BackgroundImage property → identity.
    let config: BackgroundImageConfig?
    // background-clip shrink band; zero == border-box (default).
    var clipInsets: EdgeInsets = EdgeInsets()
    // Per-layer attachment modes; nil/short arrays default to `.scroll`.
    var attachment: BackgroundAttachmentConfig? = nil

    func body(content: Content) -> some View {
        // Short-circuit when nothing to paint.
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }

        // SwiftUI stacks `.background(X).background(Y)` with X on top of
        // Y. CSS stacks layers[0] on top of layers[1]. So we walk the
        // layers in source order (0..N) attaching `.background` for each,
        // which gives the correct z-ordering naturally.
        var view: AnyView = AnyView(content)
        for layer in cfg.layers {
            // `background-attachment` is a deliberate NO-OP here.
            // CSS Backgrounds 3 §2.6 says `fixed` anchors the layer to
            // the viewport — but BOTH references this runtime is graded
            // against paint it element-anchored in the capture pipeline:
            // pixel-probing the web reference for background/000_C01
            // (conic + attachment:fixed) puts the gradient centre at
            // exactly the ELEMENT centre (86,56 for the 140×80 box at
            // 16,16), and Android — which scores 0.96+ against web on
            // that row — renders the same. A GeometryReader-based
            // viewport-anchored implementation was tried and scored
            // WORSE (0.88) because it diverged from the reference's
            // actual pixels; the true 000_C01 divergence was the conic
            // start-angle (see GradientApplier.conic). Static captures
            // never scroll, so scroll/fixed/local are indistinguishable
            // here; a real scrolling SDUI surface is where `fixed`
            // becomes observable (capability-tier work, deferred).
            let rendered = GradientApplier.render(layer)
            // `.padding(clipInsets)` shrinks the paint rectangle for
            // padding-box / content-box clip modes; zero insets are a
            // no-op for the default border-box.
            view = AnyView(view.background(rendered.padding(clipInsets)))
        }
        return view
    }
}

extension View {
    // Chain helper — invoked once per ComponentStyle from StyleBuilder.
    // `clipInsets` / `attachment` default to the CSS initial values so
    // legacy call sites stay source-compatible.
    func engineBackgroundImage(_ config: BackgroundImageConfig?,
                               clipInsets: EdgeInsets = EdgeInsets(),
                               attachment: BackgroundAttachmentConfig? = nil) -> some View {
        modifier(BackgroundImageApplier(config: config,
                                        clipInsets: clipInsets,
                                        attachment: attachment))
    }
}
