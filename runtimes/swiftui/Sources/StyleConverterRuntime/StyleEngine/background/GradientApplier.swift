//
//  GradientApplier.swift
//  StyleEngine/background — Phase 4, reworked in fidelity wave 5.
//
//  Shared helpers that turn parsed BackgroundImageLayer values into
//  SwiftUI gradient Views. Lives separately from BackgroundImageApplier
//  because the layer-stacking logic alone is close to 200 lines; this
//  file owns the "one layer → one View" conversion.
//
//  Wave-5 rework (all 9 pairwise gradient rows were flagged, 0.87–0.91):
//    1. COLOUR SPACE — SwiftUI interpolates Gradient colours in a
//       perceptual space (red→blue passes washed pink); CSS legacy
//       gradients interpolate in plain sRGB (dark purple midtones,
//       css-images-3 §3.4.3 / css-color-4 §12: legacy srgb space). We
//       pre-subdivide every stop pair with sRGB-lerped micro-stops so
//       SwiftUI's own ramp is pinned to the sRGB line.
//    2. ANGLE GEOMETRY — endpoints were computed on a UNIT SQUARE, so
//       any non-square box squashed the angle (45deg on a 200×80 box
//       rendered ≈horizontal). The §3.1.1 endpoint math now runs in
//       PIXEL space (gradient-line length |w·sinθ| + |h·cosθ|).
//    3. REPEATING flavours routed to their base shape. The converter
//       drops px stop positions (period-less), and a repeating gradient
//       whose stops span the whole line is IDENTICAL to its plain
//       counterpart — which is exactly what the web reference renders —
//       but the old stub sent repeating-RADIAL through the vertical
//       LINEAR path (PW_Background_Sizing_03, red→blue bars).
//

import SwiftUI

enum GradientApplier {

    // Render one layer to an opaque `some View`. Returns an erased view
    // so BackgroundImageApplier can iterate and stack freely.
    static func render(_ layer: BackgroundImageLayer) -> AnyView {
        switch layer {
        case .none:
            // CSS `none` layer — paint Clear so stacking order is preserved.
            return AnyView(Color.clear)
        case .url:
            // URL/data-URI rendering is a non-goal for Phase 4; paint
            // Clear so the stack index math still works. Documented
            // limitation in the Phase 4 report.
            return AnyView(Color.clear)
        case .linear(let angle, let stops):
            return AnyView(linear(angle: angle, stops: stops))
        case .radial(let shape, let stops, let cx, let cy):
            return AnyView(radial(shape: shape, stops: stops, cx: cx, cy: cy))
        case .conic(let from, let stops, let cx, let cy):
            return AnyView(conic(fromDeg: from, stops: stops, cx: cx, cy: cy))
        case .repeating(let kind, let angle, let stops):
            // Wave 5: period-less repeating layers equal their plain base
            // flavour (header note 3) — dispatch on the base kind.
            switch kind {
            case .radial: return AnyView(radial(shape: "circle", stops: stops,
                                                cx: 0.5, cy: 0.5))
            case .conic:  return AnyView(conic(fromDeg: angle, stops: stops,
                                               cx: 0.5, cy: 0.5))
            case .linear: return AnyView(linear(angle: angle, stops: stops))
            }
        }
    }

    // ── Stop resolution (sRGB space, wave 5) ──────────────────────────

    /// One resolved stop: concrete sRGB components + 0…1 location.
    /// Internal (not private) so XCTest can pin the interpolation.
    struct RGBAStop: Equatable {
        var r: Double, g: Double, b: Double, a: Double
        var loc: Double
    }

    /// Resolve declared stops to concrete sRGB + location pairs.
    /// Position rules unchanged from Phase 4: declared 0…1 positions win;
    /// nil positions fall back to an even spread over the stop count.
    /// Dynamic/unknown colours resolve to transparent (same visual as the
    /// old `.clear` fallback).
    static func resolveStops(_ stops: [BackgroundImageStop]) -> [RGBAStop] {
        let total = max(1, stops.count - 1)
        return stops.enumerated().map { i, s in
            // Static sRGB block or transparent fallback.
            let (r, g, b, a): (Double, Double, Double, Double) = {
                if case .srgb(let r, let g, let b, let a) = s.color {
                    return (r, g, b, a)
                }
                return (0, 0, 0, 0)
            }()
            return RGBAStop(r: r, g: g, b: b, a: a,
                            loc: s.position ?? (Double(i) / Double(total)))
        }
    }

    /// Subdivide each adjacent stop pair with sRGB-lerped micro-stops so
    /// SwiftUI's internal (perceptual) interpolation is constrained to
    /// segments too small to drift off the CSS sRGB ramp (header note 1).
    static func srgbSubdivided(_ stops: [RGBAStop],
                               segments: Int = 12) -> [RGBAStop] {
        // 0/1 stops have nothing to interpolate.
        guard stops.count >= 2 else { return stops }
        var out: [RGBAStop] = [stops[0]]
        for i in 1..<stops.count {
            let s0 = stops[i - 1], s1 = stops[i]
            // Straight sRGB component lerp — the legacy CSS behaviour.
            for k in 1...segments {
                let t = Double(k) / Double(segments)
                out.append(RGBAStop(r: s0.r + (s1.r - s0.r) * t,
                                    g: s0.g + (s1.g - s0.g) * t,
                                    b: s0.b + (s1.b - s0.b) * t,
                                    a: s0.a + (s1.a - s0.a) * t,
                                    loc: s0.loc + (s1.loc - s0.loc) * t))
            }
        }
        return out
    }

    // Convert the CSS stop list to a SwiftUI Gradient on the sRGB ramp.
    // Internal (not private): BackgroundGradientTileView reuses it so the
    // Canvas tile path shades the EXACT same subdivided ramp as the
    // full-box view path — any divergence would show as a colour seam
    // between the default and geometry-routed renderings of one fixture.
    static func toGradient(_ stops: [BackgroundImageStop]) -> Gradient {
        let fine = srgbSubdivided(resolveStops(stops))
        return Gradient(stops: fine.map {
            // `Color(red:green:blue:opacity:)` is sRGB by default —
            // matching the resolved component space.
            Gradient.Stop(color: Color(red: $0.r, green: $0.g, blue: $0.b,
                                       opacity: $0.a),
                          location: CGFloat($0.loc))
        })
    }

    // ── Linear ─────────────────────────────────────────────────────────

    /// css-images-3 §3.1.1 endpoint math in PIXEL space (wave 5): the
    /// gradient line passes through the box centre at CSS angle θ
    /// (clockwise from north) with length |w·sinθ| + |h·cosθ| — the
    /// perpendiculars through its endpoints touch the box corners.
    /// Returned as UnitPoints for SwiftUI (may exceed 0…1 — fine).
    /// Internal so XCTest pins the geometry.
    static func linearEndpoints(angleDeg: Double,
                                size: CGSize) -> (start: UnitPoint, end: UnitPoint) {
        let rad = angleDeg * .pi / 180
        let s = sin(rad), c = cos(rad)
        let w = max(Double(size.width), 0.001)
        let h = max(Double(size.height), 0.001)
        // Gradient-line length in pixels (§3.1.1).
        let len = abs(w * s) + abs(h * c)
        // Direction unit vector in screen coords: 0deg → up = (0, −1).
        let dx = s * len / 2, dy = -c * len / 2
        return (UnitPoint(x: 0.5 - dx / w, y: 0.5 - dy / h),
                UnitPoint(x: 0.5 + dx / w, y: 0.5 + dy / h))
    }

    // LinearGradient constructor. `angle` default per CSS spec = 180deg
    // (to bottom). GeometryReader supplies the pixel box — inside the
    // `.background` slot it is proposed exactly the element's size.
    private static func linear(angle: Double?, stops: [BackgroundImageStop]) -> some View {
        GeometryReader { geo in
            let (s, e) = linearEndpoints(angleDeg: angle ?? 180, size: geo.size)
            LinearGradient(gradient: toGradient(stops), startPoint: s, endPoint: e)
        }
    }

    // ── Radial ─────────────────────────────────────────────────────────

    /// Pre-squash radius of the DEFAULT ellipse ending shape, evaluated
    /// on the max(w,h) square the render-circular-then-stretch trick
    /// shades. css-images-3 §3.5: the default size is `farthest-corner`,
    /// whose ellipse "has the same aspect ratio [as] `farthest-side`"
    /// but is scaled to pass THROUGH the farthest corner. For a centred
    /// gradient the farthest-side aspect is (w/2 : h/2); putting the
    /// corner (w/2, h/2) on the ellipse (k·w/2, k·h/2) gives
    /// 1/k² + 1/k² = 1 → k = √2, i.e. radii (√2·w/2, √2·h/2) — exactly
    /// what Compose and Chromium paint. The pre-fix code stopped at
    /// (w/2, h/2), the farthest-SIDE ellipse: every default-radial fade
    /// on iOS ended 29% early (the live 0.85-gate residual). After the
    /// (w/m, h/m) squash a pre-squash radius of √2·m/2 lands on the
    /// √2-scaled per-axis radii, so this helper returns that value.
    /// Off-centre gradients still approximate with the centred factor —
    /// the off-centre refinement is a recorded follow-up. Internal so
    /// XCTest pins the factor once for all three call sites (view path
    /// here, MaskApplier, BackgroundGradientTileView).
    static func ellipseEndRadius(_ size: CGSize) -> CGFloat {
        // √2 × half the squash-source square's side (see doc above).
        max(size.width, size.height) / 2 * CGFloat(2.0.squareRoot())
    }

    // Radial fills from the centre out. Per CSS Images Module 3 §3.5
    // the default ending shape is `ellipse` and the default sizing is
    // `farthest-corner`. GeometryReader reads the actual bounds; for
    // `circle` a single radius (half-diagonal ≈ farthest-corner), for
    // the default `ellipse` the circular SwiftUI gradient is stretched
    // to the box aspect via scaleEffect.
    private static func radial(shape: String?, stops: [BackgroundImageStop],
                               cx: Double, cy: Double) -> some View {
        let unitCenter = UnitPoint(x: cx, y: cy)
        return GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            // Half-diagonal — distance to the farthest corner from the
            // centre (closest match to CSS `farthest-corner` default).
            let halfDiag = sqrt(w * w + h * h) / 2
            if shape == "circle" {
                RadialGradient(gradient: toGradient(stops),
                               center: unitCenter,
                               startRadius: 0,
                               endRadius: halfDiag)
                    .frame(width: w, height: h)
            } else {
                // Ellipse default — render at the √2-scaled larger-axis
                // radius (farthest-CORNER, see ellipseEndRadius) and
                // stretch to the box's aspect. The gradient function
                // extends past the square frame; only the frame is
                // visible, so an endRadius larger than the frame simply
                // moves the 100% stop outside it — which is the point.
                RadialGradient(gradient: toGradient(stops),
                               center: unitCenter,
                               startRadius: 0,
                               endRadius: ellipseEndRadius(geo.size))
                    .frame(width: max(w, h), height: max(w, h))
                    .scaleEffect(x: w / max(w, h), y: h / max(w, h), anchor: .center)
                    .frame(width: w, height: h)
            }
        }
    }

    // ── Conic ──────────────────────────────────────────────────────────

    // AngularGradient is SwiftUI's conic equivalent. CSS's `from <angle>`
    // sets the starting position. The −90° offset maps CSS's 0deg-at-12-
    // o'clock convention onto SwiftUI's trailing-edge zero (wave 1 fix).
    private static func conic(fromDeg: Double?, stops: [BackgroundImageStop],
                              cx: Double, cy: Double) -> some View {
        AngularGradient(gradient: toGradient(stops),
                        center: UnitPoint(x: cx, y: cy),
                        angle: .degrees((fromDeg ?? 0) - 90))
    }
}
