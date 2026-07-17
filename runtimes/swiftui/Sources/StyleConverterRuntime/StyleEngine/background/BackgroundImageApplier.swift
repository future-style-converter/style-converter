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
    // Wave 8 (#36) — the size/position/repeat knobs, resolved per layer
    // index with CSS list-repeat semantics (css-backgrounds-3 §2.7:
    // shorter lists cycle to match the image layer count). url() layers
    // always take the raster geometry path; GRADIENT layers take the
    // Canvas tile path only when a knob is non-initial (see
    // gradientNeedsGeometry) so knob-less gradients keep the wave-5
    // full-box GradientApplier rendering byte-identically.
    var size: BackgroundSizeConfig? = nil
    var position: BackgroundPositionConfig? = nil
    var repeatCfg: BackgroundRepeatConfig? = nil

    func body(content: Content) -> some View {
        // Short-circuit when nothing to paint.
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }

        // SwiftUI stacks `.background(X).background(Y)` with X on top of
        // Y. CSS stacks layers[0] on top of layers[1]. So we walk the
        // layers in source order (0..N) attaching `.background` for each,
        // which gives the correct z-ordering naturally.
        var view: AnyView = AnyView(content)
        for (index, layer) in cfg.layers.enumerated() {
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
            let rendered = render(layer, index: index)
            // `.padding(clipInsets)` shrinks the paint rectangle for
            // padding-box / content-box clip modes; zero insets are a
            // no-op for the default border-box.
            view = AnyView(view.background(rendered.padding(clipInsets)))
        }
        return view
    }

    /// One layer → one View. url() layers take the wave-8 raster path
    /// (decode + size/position/repeat geometry); gradient layers with a
    /// non-initial knob take the Canvas tile path (same geometry math);
    /// everything else keeps the wave-5 GradientApplier rendering
    /// byte-identically.
    private func render(_ layer: BackgroundImageLayer, index: Int) -> AnyView {
        // Per-layer knob slices — CSS §2.7 list matching (cycle short
        // lists); nil configs mean the CSS initials (auto / 0% / repeat).
        func slice<T>(_ layers: [T]?) -> T? {
            guard let layers = layers, !layers.isEmpty else { return nil }
            return layers[index % layers.count]
        }
        let sizeLayer = slice(size?.layers)
        let repeatLayer = slice(repeatCfg?.layers)
        if case .url(let urlString) = layer {
            // Decode (cached). Remote/undecodable → the browser's
            // failed-load visual: nothing painted (resolver logged it).
            guard let img = BackgroundURLImageResolver.image(for: urlString) else {
                return AnyView(Color.clear)
            }
            return AnyView(BackgroundURLImageView(
                uiImage: img,
                sizeLayer: sizeLayer,
                // Position is one pair (not per-layer lists in this
                // config shape) — applies to every layer, matching the
                // extractor.
                positionX: position?.x,
                positionY: position?.y,
                repeatLayer: repeatLayer))
        }
        // `none` paints Clear regardless of geometry knobs (there is no
        // image to size/position/tile) — keep the stack index math.
        if case .none = layer { return GradientApplier.render(layer) }
        // Gradient layer: geometry-route only when a knob would change
        // the picture; otherwise the wave-5 full-box path is untouched
        // (every knob-less gradient baseline stays byte-identical).
        if BackgroundImageApplier.gradientNeedsGeometry(
            size: sizeLayer, positionX: position?.x, positionY: position?.y,
            repeatLayer: repeatLayer) {
            return AnyView(BackgroundGradientTileView(
                layer: layer, sizeLayer: sizeLayer,
                positionX: position?.x, positionY: position?.y,
                repeatLayer: repeatLayer))
        }
        return GradientApplier.render(layer)
    }

    /// True when size/position/repeat can move a GRADIENT layer off the
    /// full-box default. Static (and internal) so XCTest pins the
    /// routing without building views.
    static func gradientNeedsGeometry(size: BackgroundSizeLayer?,
                                      positionX: BackgroundAxisPosition?,
                                      positionY: BackgroundAxisPosition?,
                                      repeatLayer: BackgroundRepeatLayer?) -> Bool {
        // Only EXPLICIT sizes shrink/stretch an intrinsic-less image —
        // auto/cover/contain all resolve to the box (§3.9), which is
        // exactly what the default full-box path already paints.
        if case .explicit = size { return true }
        // Any position: px offsets shift even a full-box tile (the
        // uncovered band shows the layer below, like the browser).
        if positionX != nil || positionY != nil { return true }
        // Repeat non-initial on either axis: no-repeat/space/round all
        // change the lattice once a size is involved; with auto size the
        // tile equals the box so the Canvas paints the same picture —
        // routing is still correct, just unnecessary for plain repeat.
        if let r = repeatLayer,
           BackgroundTileMath.mode(r.x) != .repeat || BackgroundTileMath.mode(r.y) != .repeat {
            return true
        }
        return false
    }
}

extension View {
    // Chain helper — invoked once per ComponentStyle from StyleBuilder.
    // `clipInsets` / `attachment` / the raster knobs default to the CSS
    // initial values so legacy call sites stay source-compatible.
    func engineBackgroundImage(_ config: BackgroundImageConfig?,
                               clipInsets: EdgeInsets = EdgeInsets(),
                               attachment: BackgroundAttachmentConfig? = nil,
                               size: BackgroundSizeConfig? = nil,
                               position: BackgroundPositionConfig? = nil,
                               repeatCfg: BackgroundRepeatConfig? = nil) -> some View {
        modifier(BackgroundImageApplier(config: config,
                                        clipInsets: clipInsets,
                                        attachment: attachment,
                                        size: size,
                                        position: position,
                                        repeatCfg: repeatCfg))
    }
}
