//
//  GradientApplier+Stops.swift
//  StyleEngine/background — stop resolution, split out of
//  GradientApplier.swift in wave 46 (lane Y2) when the css-images-4
//  §3.4.3 pipeline (GradientStopResolver + GradientRamp) joined the
//  wave-5 sRGB subdivision. Same type, same internal API — XCTest and
//  the mask/tile painters keep calling `GradientApplier.toGradient`,
//  `srgbSubdivided`, `resolveStops` and `RGBAStop` unchanged.
//

import SwiftUI

extension GradientApplier {

    // ── Stop resolution (sRGB space, wave 5) ──────────────────────────

    /// One resolved stop: concrete sRGB components + 0…1 location.
    /// Internal (not private) so XCTest can pin the interpolation.
    struct RGBAStop: Equatable {
        var r: Double, g: Double, b: Double, a: Double
        var loc: Double
    }

    /// Resolve declared stops to concrete sRGB + location pairs via the
    /// css-images-4 §3.4.3 fixup (wave 46). For an all-nil stop list the
    /// result is exactly the Phase-4 even spread i/(n−1); declared
    /// percents still win; a stop behind its predecessor now clamps
    /// forward instead of being handed to SwiftUI out of order.
    /// `lengthPx` resolves <length> stops; nil (conic, or callers with no
    /// geometry) leaves them unpositioned with a breadcrumb. Dynamic /
    /// unknown colours resolve to transparent (same visual as before).
    static func resolveStops(_ stops: [BackgroundImageStop],
                             lengthPx: Double? = nil) -> [RGBAStop] {
        GradientStopResolver.fixup(stops, lengthPx: lengthPx)
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
    static func toGradient(_ stops: [BackgroundImageStop],
                           lengthPx: Double? = nil,
                           repeating: Bool = false) -> Gradient {
        let fine = resolvedRamp(stops, lengthPx: lengthPx, repeating: repeating)
        return Gradient(stops: fine.map {
            // `Color(red:green:blue:opacity:)` is sRGB by default —
            // matching the resolved component space.
            Gradient.Stop(color: Color(red: $0.r, green: $0.g, blue: $0.b,
                                       opacity: $0.a),
                          location: CGFloat($0.loc))
        })
    }

    /// The full stop pipeline (wave 46): §3.4.3 fixup → repeating
    /// expansion → unit clip → subdivision. Clause-less gradients
    /// subdivide on the historical sRGB lerp (`srgbSubdivided`, 12
    /// segments) so every pre-wave capture is byte-identical — measured,
    /// not asserted: skeptic S4 (wave 46) re-rendered the 103 committed
    /// dark-stage fixtures through this path and got 103/103 identical.
    /// Authored `interp` clauses take GradientRamp's css-color-4 §12 path.
    /// Internal so XCTest pins the composition without a View.
    static func resolvedRamp(_ stops: [BackgroundImageStop],
                             lengthPx: Double?, repeating: Bool) -> [RGBAStop] {
        // Gradient-level attribute stamped on every stop (see config).
        let interp = stops.first?.interp ?? .legacy
        var ramp = GradientStopResolver.fixup(stops, lengthPx: lengthPx)
        if repeating { ramp = GradientStopResolver.expandRepeating(ramp) }
        ramp = GradientStopResolver.clipToUnit(ramp, interp: interp)
        if interp.space == .legacy {
            // 12 segments unless a repeating lattice would push the stop
            // count past ~600 — then fewer per pair (same cap GradientRamp
            // applies), never more.
            let perPair = max(1, min(12, 600 / max(1, ramp.count - 1)))
            return srgbSubdivided(ramp, segments: perPair)
        }
        return GradientRamp.subdivided(ramp, interp: interp)
    }

    /// css-images-3 §3.1.1 gradient-line LENGTH in px — the quantity a
    /// <length> stop position divides by. Same formula linearEndpoints
    /// uses for its endpoints (|w·sinθ| + |h·cosθ|).
    static func lineLengthPx(angleDeg: Double, size: CGSize) -> Double {
        let rad = angleDeg * .pi / 180
        return abs(Double(size.width) * sin(rad)) + abs(Double(size.height) * cos(rad))
    }
}
