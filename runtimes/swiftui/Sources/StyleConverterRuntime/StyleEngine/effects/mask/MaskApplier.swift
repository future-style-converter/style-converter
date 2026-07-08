//
//  MaskApplier.swift
//  StyleEngine/effects/mask — Phase 8.
//
//  Applies a `MaskConfig` via SwiftUI `.mask(_:)`. Gradient layers are
//  the common case and translate cleanly — a LinearGradient from opaque
//  black to transparent creates the same alpha profile as the CSS
//  equivalent. URL references and mask-border are TODOs.
//

import SwiftUI

struct MaskApplier: ViewModifier {
    let config: MaskConfig?

    func body(content: Content) -> some View {
        // Short-circuit when nothing was declared.
        guard let cfg = config, cfg.touched else { return AnyView(content) }
        // Mask images — stack layers in a ZStack if multiple are
        // supplied. This isn't a true CSS composite (which uses
        // MaskComposite to combine layers) but is a reasonable
        // approximation for the add-mode default.
        guard !cfg.images.isEmpty else { return AnyView(content) }
        // CSS spec: `mask-image: none` is the initial value and means no
        // mask. If every declared layer is `.none`, short-circuit before
        // applying `.mask(Color.clear)` which would erase the entire view.
        if cfg.images.allSatisfy({ if case .none = $0 { return true }; return false }) {
            return AnyView(content)
        }

        let mask = AnyView(
            ZStack {
                ForEach(Array(cfg.images.enumerated()), id: \.offset) { _, layer in
                    buildLayer(layer, cfg: cfg)
                }
            }
        )
        return AnyView(content.mask(mask))
    }

    // Build one mask layer as a View. Percentage positioning is honoured
    // for gradients via an Alignment offset.
    @ViewBuilder
    private func buildLayer(_ layer: MaskLayer, cfg: MaskConfig) -> some View {
        switch layer {
        case .none:
            // No-op — we still render a transparent colour so the ZStack
            // survives the ForEach indexing contract.
            Color.clear
        case .url:
            // No SwiftUI analog for `url(#id)` — paint a neutral grey
            // so the user sees that SOMETHING is being masked.
            Color.black.opacity(0.5)
        case .linearGradient(let deg, let stops):
            // CSS angle convention: 0deg points UP, rotating clockwise.
            // SwiftUI LinearGradient's startPoint/endPoint interprets
            // differently — we fake it by constructing unit vectors.
            LinearGradient(gradient: Gradient(stops: stops),
                           startPoint: startPoint(forAngle: deg),
                           endPoint: endPoint(forAngle: deg))
        case .radialGradient(let stops):
            // Radial: centre at mask position (defaults 50% 50%).
            RadialGradient(gradient: Gradient(stops: stops),
                           center: UnitPoint(x: cfg.position.x, y: cfg.position.y),
                           startRadius: 0, endRadius: 200)
        case .conicGradient(let stops):
            // Conic: SwiftUI's AngularGradient sweeps 360° from 0rad.
            AngularGradient(gradient: Gradient(stops: stops),
                            center: UnitPoint(x: cfg.position.x, y: cfg.position.y))
        }
    }

    // Helpers — map CSS angle (in degrees, 0 = up) to SwiftUI
    // startPoint/endPoint on the unit square.
    private func startPoint(forAngle deg: Double) -> UnitPoint {
        // Convert so 0deg → bottom→top, 90deg → left→right, etc.
        let rad = (deg - 90) * .pi / 180
        let x = 0.5 - 0.5 * cos(rad)
        let y = 0.5 + 0.5 * sin(rad)
        return UnitPoint(x: x, y: y)
    }
    private func endPoint(forAngle deg: Double) -> UnitPoint {
        let rad = (deg - 90) * .pi / 180
        let x = 0.5 + 0.5 * cos(rad)
        let y = 0.5 - 0.5 * sin(rad)
        return UnitPoint(x: x, y: y)
    }
}

extension View {
    // Identity on nil.
    func engineMask(_ config: MaskConfig?) -> some View {
        modifier(MaskApplier(config: config))
    }
}
