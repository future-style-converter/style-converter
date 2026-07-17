//
//  MaskApplier.swift
//  StyleEngine/effects/mask — Phase 8.
//
//  Applies a `MaskConfig` via SwiftUI `.mask(_:)`. Gradient layers are
//  the common case and translate cleanly — a LinearGradient from opaque
//  black to transparent creates the same alpha profile as the CSS
//  equivalent. url() layers resolve to real rasters via MaskURLLayer
//  (shared background decode + tiling pipeline); mask-border is a TODO.
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
        case .url(let href):
            // Real raster masks: MaskURLLayer resolves the href through
            // the shared background url() pipeline and tiles it per
            // cfg.size/position/repeat (BackgroundImageGeometry math).
            // Unresolvable sources (url(#fragment) SVG refs, http(s)
            // remotes, broken payloads) render Color.clear — the
            // css-masking-1 failed-load semantics (transparent black
            // layer = element hidden), replacing the old misleading
            // Color.black.opacity(0.5) placeholder that showed the
            // element uniformly half-visible.
            MaskURLLayer.view(href, cfg: cfg)
        case .linearGradient(let deg, let stops, let repeating):
            // Endpoints REUSE GradientApplier.linearEndpoints — the
            // background path's css-images-3 §3.1.1 pixel-space math.
            // The old private startPoint/endPoint here used y-UP trig,
            // which INVERTED every vertical mask (180deg = "to bottom"
            // faded bottom→top while web faded top→bottom). Sharing the
            // background helper makes the two linear paths identical by
            // construction; GeometryReader supplies the pixel box the
            // §3.1.1 length formula needs (a unit square squashes
            // diagonal angles on non-square boxes).
            GeometryReader { geo in
                let (s, e) = Self.linearMaskEndpoints(angleDeg: deg, size: geo.size)
                // Repeating layers: SwiftUI clamps after the last stop,
                // so tile the first→last span across [0,1] first
                // (§3.4.3) — see expandRepeatingStops.
                LinearGradient(
                    gradient: Gradient(stops: stopsFor(
                        repeating ? Self.expandRepeatingStops(stops) : stops)),
                    startPoint: s, endPoint: e)
            }
        case .radialGradient(let shape, let stops):
            // Mirrors GradientApplier.radial (the background path) so
            // mask and background radials render identically: CSS
            // defaults are ending shape ELLIPSE sized farthest-corner
            // (css-images-3 §3.5). The old branch hardcoded endRadius
            // 200 — on a 160×80 fixture the fade barely started before
            // the edges; on large boxes it died long before the corner.
            GeometryReader { geo in
                let w = geo.size.width
                let h = geo.size.height
                let center = UnitPoint(x: cfg.position.x, y: cfg.position.y)
                if shape == "circle" {
                    // Half-diagonal — distance to the farthest corner
                    // (same approximation as the background circle path).
                    RadialGradient(gradient: Gradient(stops: stopsFor(stops)),
                                   center: center,
                                   startRadius: 0,
                                   endRadius: sqrt(w * w + h * h) / 2)
                        .frame(width: w, height: h)
                } else {
                    // Ellipse default (nil or explicit "ellipse"):
                    // render circular at the √2-scaled larger axis then
                    // stretch to the box aspect — RadialGradient is
                    // circular-only, exactly like the background ellipse
                    // branch. The √2 comes from css-images-3 §3.5
                    // farthest-CORNER sizing (radii √2·w/2 × √2·h/2 for
                    // a centred gradient — see
                    // GradientApplier.ellipseEndRadius); the old
                    // max(w,h)/2 stopped at the farthest SIDE, ending
                    // every default-radial mask fade 29% early vs
                    // web/Compose.
                    RadialGradient(gradient: Gradient(stops: stopsFor(stops)),
                                   center: center,
                                   startRadius: 0,
                                   endRadius: GradientApplier.ellipseEndRadius(geo.size))
                        .frame(width: max(w, h), height: max(w, h))
                        .scaleEffect(x: w / max(w, h), y: h / max(w, h), anchor: .center)
                        .frame(width: w, height: h)
                }
            }
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

    /// Linear-mask endpoints — a pure delegate to
    /// GradientApplier.linearEndpoints so the css-images-3 §3.1.1
    /// geometry has exactly ONE owner shared by background and mask
    /// linears. (The replaced private trig here computed y-UP
    /// coordinates: 180deg produced start=(0.5, 1) → end=(0.5, 0),
    /// i.e. bottom→top — the inverted-vertical-mask bug.) Internal so
    /// XCTest pins the delegation and the fixed direction.
    static func linearMaskEndpoints(angleDeg: Double,
                                    size: CGSize) -> (start: UnitPoint, end: UnitPoint) {
        GradientApplier.linearEndpoints(angleDeg: angleDeg, size: size)
    }

    /// Tile a repeating gradient's stop span across the whole gradient
    /// line. css-images-3 §3.4.3: the first→last stop span is the
    /// period, repeated infinitely in BOTH directions; SwiftUI's
    /// Gradient instead clamps outside its stops, so we synthesize the
    /// copies that overlap [0,1]. Copies protruding past the line are
    /// clamped to the boundary (the boundary stop keeps its own colour —
    /// a ≤1-period-wide approximation at the very edge, acceptable for
    /// masks). Degenerate spans return the stops unchanged: zero width
    /// has no finite period, and a full-line span already equals the
    /// plain gradient. Capped at 64 periods per direction — beyond that
    /// the stripes are sub-pixel at fixture scale and only bloat the
    /// stop list. Pure math; pinned by MaskFidelityTests.
    static func expandRepeatingStops(_ stops: [Gradient.Stop]) -> [Gradient.Stop] {
        guard stops.count >= 2,
              let first = stops.first?.location,
              let last = stops.last?.location,
              // Zero-width span (no period) or full-line span (repeats
              // to itself) — both render identically to the plain stops.
              last > first, (first > 0 || last < 1) else { return stops }
        let period = last - first
        // Copy k spans [first + k·period, last + k·period]; keep every
        // copy overlapping the [0,1] line (both directions, capped).
        let kMin = max(Int(floor((0 - last) / period)) + 1, -64)
        let kMax = min(Int(ceil((1 - last) / period)), 64)
        var out: [Gradient.Stop] = []
        for k in kMin...kMax {
            let shift = CGFloat(k) * period
            for s in stops {
                // Clamp boundary copies onto the line ends.
                out.append(Gradient.Stop(color: s.color,
                                         location: min(max(s.location + shift, 0), 1)))
            }
        }
        return out
    }
}

extension View {
    // Identity on nil.
    func engineMask(_ config: MaskConfig?) -> some View {
        modifier(MaskApplier(config: config))
    }
}
