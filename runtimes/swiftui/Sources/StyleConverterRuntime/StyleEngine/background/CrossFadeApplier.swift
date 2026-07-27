//
//  CrossFadeApplier.swift
//  StyleEngine/background — wave-21 IMAGES lane (A-RC2).
//
//  The cross-fade() compositor + the <color>-as-image fill, split out of
//  GradientApplier.swift when that file crossed the ~300-line split rule.
//  Extends GradientApplier so `render(_:)` keeps one dispatch surface.
//

import SwiftUI

extension GradientApplier {

    // ── <color> as image (cross-fade argument) ────────────────────────

    /// Resolve a ColorValue to a solid fill. The static sRGB branch is
    /// the only one cross-fade arguments produce (the converter resolves
    /// color() functions to sRGB); dynamic/unknown falls back to clear —
    /// the same fallback the stop resolver applies.
    static func solidColor(_ cv: ColorValue) -> Color {
        if case .srgb(let r, let g, let b, let a) = cv {
            // Color(red:green:blue:opacity:) is sRGB by default — the
            // resolved component space (matches toGradient's stops).
            return Color(red: r, green: g, blue: b, opacity: a)
        }
        return Color.clear
    }

    // ── cross-fade() compositor (css-images-4 §2.6.2) ─────────────────

    /// The spec defines the result as the WEIGHTED SUM of the
    /// premultiplied images: `result = Σ wᵢ × premult(imageᵢ)` — including
    /// alpha, so six 10% layers of an opaque gradient yield EXACTLY alpha
    /// 0.6 (the target-alpha WPT). Sequential src-over stacking would give
    /// 1−0.9⁶ ≈ 0.47 — wrong — so each image renders at `.opacity(wᵢ)`
    /// (premultiplied scale) with `.blendMode(.plusLighter)` (component-
    /// wise premultiplied ADD) inside a `.compositingGroup()` that
    /// isolates the additive stack over a TRANSPARENT base. Premultiplied
    /// behaviour (the premultiplied-alpha WPT: 1%-alpha red + opaque
    /// green → translucent green, near-zero red) falls out of plusLighter
    /// operating on premultiplied components — the same math as the
    /// Compose twin's BlendMode.Plus saveLayer (ColorApplier.applyCrossFade).
    static func crossFade(_ args: [CrossFadeArg]) -> some View {
        ZStack {
            // Additive blending is order-independent; indices only key
            // the ForEach identity.
            ForEach(0..<args.count, id: \.self) { i in
                render(args[i].layer)
                    .opacity(args[i].weight)
                    .blendMode(.plusLighter)
            }
        }
        // Isolate: without the group, plusLighter would add into whatever
        // the page already painted below this layer.
        .compositingGroup()
    }
}
