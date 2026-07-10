//
//  BoxShadowApplier.swift
//  StyleEngine/effects/shadow — Phase 5.
//
//  Stacks one SwiftUI `.shadow(...)` per outset layer, and draws inset
//  layers as an overlay clipped to the rounded-box interior. Multiple
//  layers are supported because SwiftUI's `.shadow` is composable:
//  every call wraps the view in a new shadow-generating layer, so
//  repeated calls produce CSS-accurate stacking.
//
//  Spread is not a SwiftUI primitive; we approximate by scaling the
//  overlay shape by `spread / min(width,height)` before applying the
//  shadow. That's noticeably off for large spreads — documented
//  limitation, matches Android's shape of the workaround.
//
//  Blur radius: CSS blur roughly equals 2× SwiftUI's `.shadow(radius:)`
//  visually. We divide by 2 at apply time so the three renderers
//  look close enough for SSIM parity.
//

// SwiftUI for shadow / overlay primitives.
import SwiftUI

struct BoxShadowApplier: ViewModifier {
    // Nil / empty → identity.
    let config: BoxShadowConfig?
    // Optional radius so the inset-shadow mask hugs the rounded shape.
    let radius: BorderRadiusConfig?

    func body(content: Content) -> some View {
        guard let cfg = config, cfg.hasAny else { return AnyView(content) }
        // Split into outset + inset — order preserved within each bucket
        // so painting order follows CSS semantics.
        let outset = cfg.layers.filter { !$0.inset }
        let inset  = cfg.layers.filter {  $0.inset }

        // Start with the bare content; iterate outset layers from the
        // innermost (first in CSS) to the outermost so they compose
        // correctly. SwiftUI applies `.shadow` to the most recent view.
        var v: AnyView = AnyView(content)
        for layer in outset {
            let colour = layer.color ?? .black.opacity(0.25)
            // CSS blur ≈ 2× SwiftUI radius.
            let radius = layer.blur / 2
            if layer.spread > 0 {
                // Spread path — SwiftUI's `.shadow(...)` doesn't take a
                // spread argument, so the only-spread case (`box-shadow:
                // 0 0 0 4px blue`) used to render nothing on iOS while
                // Android (`Compose Modifier.shadow` with native spread)
                // and web both painted a hard blue ring. Workaround:
                // draw a copy of the rounded shape behind the element,
                // expanded by `spread` on every side via a negative
                // padding. The shape is drawn at the shadow colour and
                // optionally blurred + offset to approximate the rest of
                // the layer's parameters. `.padding(-spread)` is the
                // standard SwiftUI idiom for "give me this much extra
                // size beyond my parent's bounds" — the shape paints
                // across the inflated frame, producing the spread halo.
                let cornerRadius = self.radius ?? BorderRadiusConfig()
                // Fidelity wave 1: CSS Backgrounds 3 §7.1.1 — an OUTER
                // shadow "is clipped out" over the border-box area: it
                // must never paint UNDER the element. The old negative-
                // padding fill covered the whole border box too, which
                // was invisible under opaque elements but tinted every
                // translucent one — borders/016_Decorated's `opacity:
                // 0.85` ellipse composited the blue ring through its
                // whole fill (pixel-probe: iOS fill (214,153,44) vs
                // web's clean (210,137,22)). Paint the spread halo,
                // then punch the border box back out with a
                // destination-out layer inside one compositing group.
                v = AnyView(v.background(
                    ZStack {
                        // The spread-expanded silhouette, blurred and
                        // offset per the layer (blur ≈ CSS/2, see header).
                        BorderRadiusShape(radius: cornerRadius)
                            .inset(by: -layer.spread)
                            .fill(colour)
                            .blur(radius: radius)
                            .offset(x: layer.x, y: layer.y)
                        // §7.1.1 clip: erase the border-box region so
                        // nothing of the shadow survives under the
                        // (possibly translucent) element itself.
                        BorderRadiusShape(radius: cornerRadius)
                            .fill(Color.black)
                            .blendMode(.destinationOut)
                    }
                    // The punch-out only composites against THIS pair —
                    // without the group it would erase the canvas too.
                    .compositingGroup()
                ))
            } else {
                // Pure blur (or zero) shadow — keep the lightweight
                // `.shadow(...)` path. It's a single Core Animation layer
                // and doesn't allocate an extra view.
                v = AnyView(v.shadow(color: colour, radius: radius,
                                     x: layer.x, y: layer.y))
            }
        }

        // Inset layers go on top as an overlay masked to the element's
        // rounded shape. We draw a rectangle offset opposite to the
        // shadow direction so SwiftUI's blur can leak into the interior.
        if !inset.isEmpty {
            let shape = BorderRadiusShape(radius: radius ?? BorderRadiusConfig())
            v = AnyView(v.overlay(
                ZStack {
                    ForEach(Array(inset.enumerated()), id: \.offset) { _, layer in
                        InsetShadow(layer: layer, shape: shape)
                    }
                }
                .allowsHitTesting(false)
            ))
        }
        return v
    }
}

// Private helper — one inset layer. Builds a rectangle that covers the
// element, shifts it by the CSS offset, and strokes with a large blurred
// colour so the blur leaks inside the masked region.
private struct InsetShadow: View {
    let layer: BoxShadowLayer
    let shape: BorderRadiusShape

    var body: some View {
        let colour = layer.color ?? .black.opacity(0.35)
        let radius = layer.blur / 2
        // Strategy: draw a stroked ring just inside the element with the
        // shadow colour, then blur it. `mask: shape` keeps the effect
        // clipped to the element's rounded box, giving a classic inner-
        // shadow look. This trades fidelity for simplicity — SwiftUI
        // has no true "inset shadow" primitive.
        shape
            .stroke(colour, lineWidth: max(layer.blur, 1))
            .blur(radius: radius)
            .offset(x: layer.x, y: layer.y)
            .mask(shape)
    }
}

// View chain helper.
extension View {
    func engineBoxShadow(_ config: BoxShadowConfig?,
                         radius: BorderRadiusConfig? = nil) -> some View {
        modifier(BoxShadowApplier(config: config, radius: radius))
    }
}
