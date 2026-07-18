//
//  BackgroundBlendCompositor.swift
//  StyleEngine/effects/blend — IOS-BBM lane.
//
//  The real `background-blend-mode` compositor. Per CSS Compositing 1
//  §3.2, each background-image LAYER blends against the composite of the
//  layers below it plus the `background-color` at the very bottom — and
//  the whole stack blends *in isolation*: the element's backdrop (the
//  canvas / parent) never participates. The old BlendModeApplier
//  fallback either applied the first mode to the WHOLE view (wrong —
//  multiplied red against the dark canvas → near-black, the Phase-12
//  audit regression) or, after that fix, rendered the layers unblended
//  (BBM_screen iOS↔Android 0.934, the wave-6 worst offender).
//
//  SwiftUI recipe: a ZStack ordered bottom-to-top (colour first, then
//  image layers in REVERSE source order since CSS layers[0] paints on
//  top), each image layer carrying `.blendMode(mode)`, the whole stack
//  wrapped in `.compositingGroup()`. The compositing group renders the
//  children into an isolated offscreen buffer, so each `.blendMode`
//  blends ONLY against siblings already drawn inside the buffer — the
//  §3.2 isolation — and the finished buffer composites normally against
//  whatever is behind the element. This mirrors Compose's
//  saveLayer+Paint(blendMode) stack in ColorApplier.kt (per-layer
//  saveLayer inside drawWithContent), so the two natives agree.
//

import SwiftUI

/// The isolated blended background stack. Built by BackgroundImageApplier
/// when at least one `background-blend-mode` entry is non-normal; hosted
/// inside a single `.background(...)` at the existing background-chain
/// position so paint order relative to borders/content is unchanged.
struct BackgroundBlendCompositor: View {
    /// Rendered background-image layers in SOURCE order — index 0 is the
    /// CSS top layer (css-backgrounds-3 §2: first comma item on top).
    /// Pre-rendered by BackgroundImageApplier.render so every geometry
    /// knob (size/position/repeat, url rasters) keeps working unchanged.
    let layerViews: [AnyView]
    /// Per-layer blend modes from BlendModeConfig.background. Shorter
    /// lists cycle (css-backgrounds-3 §2.7 list matching); extra entries
    /// beyond the layer count are ignored, like the browser.
    let modes: [BlendMode]
    /// The `background-color` fill (nil when the element has none) —
    /// §3.2 makes it the bottom-most layer of the blend stack, so the
    /// bottom image layer blends against it rather than the backdrop.
    /// Built by ColorApplier.fillView so the shape (radius awareness)
    /// is identical to the unblended colour paint.
    let baseFill: AnyView?

    var body: some View {
        // ZStack draws its FIRST child at the bottom — exactly the CSS
        // §3.2 stacking we need: colour, then layers bottom-to-top.
        ZStack {
            // Bottom: background-color, drawn plain (mode `normal`) —
            // the spec blends layers INTO the colour, never the colour
            // into anything.
            if let fill = baseFill { fill }
            // Image layers in reverse source order so layers[0] draws
            // LAST (= on top), matching the legacy `.background` chain.
            // `id: \.self` over plain indices is stable — the layer list
            // is immutable for a given ComponentStyle.
            ForEach(Array(layerViews.indices.reversed()), id: \.self) { i in
                // Each layer blends against everything already drawn in
                // the group (layers below + colour), per §3.2.
                layerViews[i].blendMode(mode(at: i))
            }
        }
        // The §3.2 isolation: without this, `.blendMode` children would
        // blend against the CANVAS behind the element (the Phase-12
        // near-black regression); with it they blend inside an offscreen
        // buffer that then composites normally.
        .compositingGroup()
    }

    /// §2.7 list matching — cycle a short mode list across the layers;
    /// an empty list (defensive: the applier gates on non-empty) means
    /// the initial value `normal`.
    private func mode(at index: Int) -> BlendMode {
        guard !modes.isEmpty else { return .normal }
        return modes[index % modes.count]
    }
}
