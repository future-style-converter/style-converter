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
        // `mask-mode: luminance` (CSS Masking 1 §7.3) converts the mask
        // source to a luminance channel before use: the mask value at
        // each pixel is `relativeLuminance × alpha` instead of the raw
        // alpha. SwiftUI's `.mask` only reads alpha, so we pre-fold the
        // stop colours: a BLACK stop (luminance 0) becomes fully
        // transparent — which is why web renders NOTHING for a black
        // linear-gradient mask in luminance mode while the old iOS path
        // (alpha-only) showed a gradient fade (effects/004_C05).
        let stopsFor: ([Gradient.Stop]) -> [Gradient.Stop] = { stops in
            cfg.mode == .luminance ? stops.map(Self.luminanceStop) : stops
        }
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
            LinearGradient(gradient: Gradient(stops: stopsFor(stops)),
                           startPoint: startPoint(forAngle: deg),
                           endPoint: endPoint(forAngle: deg))
        case .radialGradient(let stops):
            // Radial: centre at mask position (defaults 50% 50%).
            RadialGradient(gradient: Gradient(stops: stopsFor(stops)),
                           center: UnitPoint(x: cfg.position.x, y: cfg.position.y),
                           startRadius: 0, endRadius: 200)
        case .conicGradient(let stops):
            // Conic: CSS starts at 12 o'clock (css-images-4 §2.3) while
            // SwiftUI's AngularGradient starts at 3 o'clock — apply the
            // same −90° offset as GradientApplier.conic so mask and
            // background conics stay rotationally aligned.
            AngularGradient(gradient: Gradient(stops: stopsFor(stops)),
                            center: UnitPoint(x: cfg.position.x, y: cfg.position.y),
                            angle: .degrees(-90))
        }
    }

    /// Fold a stop's colour into its luminance-derived alpha:
    /// `alpha' = Y × alpha`, colour forced to white so only the alpha
    /// channel carries signal. Y uses the Rec.709 relative-luminance
    /// coefficients referenced by CSS Masking 1 §7.3 (via SVG 1.1
    /// `luminanceToAlpha`): Y = 0.2126 R + 0.7152 G + 0.0722 B.
    static func luminanceStop(_ stop: Gradient.Stop) -> Gradient.Stop {
        // UIColor bridge — every mask colour reaches here via
        // Color(.sRGB, …) so getRed always succeeds; fall back to the
        // stop unchanged if a pattern/named colour ever sneaks in.
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        guard UIColor(stop.color).getRed(&r, green: &g, blue: &b, alpha: &a) else {
            return stop
        }
        let y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return Gradient.Stop(color: Color.white.opacity(Double(y * a)),
                             location: stop.location)
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
