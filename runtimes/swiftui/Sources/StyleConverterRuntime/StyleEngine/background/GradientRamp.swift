//
//  GradientRamp.swift
//  StyleEngine/background — wave 46, lane Y2 (css-images natives).
//
//  The per-pair colour math of a gradient ramp: css-color-4 §12
//  interpolation between two resolved stops (premultiplied alpha, hue
//  arc selection, powerless-hue carry) and the subdivision that bakes
//  that ramp into micro-stops SwiftUI's gradient types can follow.
//  `.legacy` (no authored clause) reproduces the historical
//  non-premultiplied sRGB component lerp exactly, so every clause-less
//  capture is byte-identical to the pre-wave rendering — measured, not
//  asserted: skeptic S4 (wave 46) re-rendered the 103 committed
//  dark-stage fixtures and got 103/103 identical. Only gradients
//  carrying an `interp` clause take the spec path.
//
//  Colour-space conversions live in GradientColorMath; stop positions
//  are resolved upstream by GradientStopResolver.
//

import Foundation

enum GradientRamp {

    typealias Stop = GradientApplier.RGBAStop

    // MARK: - Interpolation (css-color-4 §12)

    /// Colour at `t` between `a` and `b` in the authored space:
    /// premultiplied non-hue components (§12.3), hue arc per method
    /// (§12.5), powerless hue carried from the other stop (§12.2).
    static func interpolate(_ a: Stop, _ b: Stop, t: Double, interp: GradientInterpolation) -> Stop {
        let space = interp.space
        var ca = comps(GradientColorMath.fromSrgb((a.r, a.g, a.b), to: space))
        var cb = comps(GradientColorMath.fromSrgb((b.r, b.g, b.b), to: space))
        let alpha = a.a + (b.a - a.a) * t
        let hueIx = GradientColorMath.hueIndex(space)
        // Missing-component carry for the hue (§12.2 "analogous"): a
        // powerless hue takes the other stop's hue before the arc is
        // chosen — `red → black in hsl longer hue` therefore sweeps the
        // whole wheel instead of fading straight to black.
        if let h = hueIx {
            let pa = GradientColorMath.hueIsPowerless((ca[0], ca[1], ca[2]), in: space)
            let pb = GradientColorMath.hueIsPowerless((cb[0], cb[1], cb[2]), in: space)
            if pa && !pb { ca[h] = cb[h] }
            if pb && !pa { cb[h] = ca[h] }
        }
        var out: [Double] = [0, 0, 0]
        for i in 0..<3 {
            let va = ca[i], vb = cb[i]
            if i == hueIx {
                out[i] = hueLerp(va, vb, t: t, method: interp.hue)
            } else if space == .legacy {
                // Historical ramp: plain component lerp, no premultiply
                // (byte-stable with the pre-wave srgbSubdivided path).
                out[i] = va + (vb - va) * t
            } else if alpha > 0 {
                // Premultiplied (§12.3): each component weighted by its
                // own alpha, un-premultiplied by the interpolated alpha.
                out[i] = (va * a.a * (1 - t) + vb * b.a * t) / alpha
            } else {
                // Both fully transparent — invisible either way.
                out[i] = va + (vb - va) * t
            }
        }
        let rgb = GradientColorMath.toSrgb((out[0], out[1], out[2]), from: space)
        return Stop(r: rgb.0, g: rgb.1, b: rgb.2, a: alpha, loc: a.loc + (b.loc - a.loc) * t)
    }

    /// Tuple → array so components are index-addressable.
    private static func comps(_ c: GradientColorMath.Triple) -> [Double] { [c.0, c.1, c.2] }

    /// css-color-4 §12.5 arc selection: the (possibly >360 / unordered)
    /// endpoint pair the lerp runs between. Exposed for the subdivision
    /// density and for XCTest.
    static func hueArc(_ h1: Double, _ h2: Double, method: GradientInterpolation.HueMethod) -> (Double, Double) {
        var a = norm(h1), b = norm(h2)
        let d = b - a
        switch method {
        case .shorter:    if d > 180 { a += 360 } else if d < -180 { b += 360 }
        case .longer:     if d > 0 && d < 180 { a += 360 } else if d > -180 && d <= 0 { b += 360 }
        case .increasing: if b < a { b += 360 }
        case .decreasing: if a < b { a += 360 }
        }
        return (a, b)
    }

    /// Hue at `t` along the selected arc, normalised to [0, 360).
    static func hueLerp(_ h1: Double, _ h2: Double, t: Double, method: GradientInterpolation.HueMethod) -> Double {
        let (a, b) = hueArc(h1, h2, method: method)
        return norm(a + (b - a) * t)
    }

    private static func norm(_ h: Double) -> Double {
        let m = h.truncatingRemainder(dividingBy: 360)
        return m < 0 ? m + 360 : m
    }

    // MARK: - Subdivision

    /// Micro-stops between each adjacent pair, computed in the authored
    /// space. Polar sweeps get one stop per ≤10° of hue so a 360° wheel
    /// is not flattened to 12 chords; `budget` caps the total so a
    /// repeating lattice with hundreds of copies stays renderable.
    static func subdivided(_ stops: [Stop], interp: GradientInterpolation, budget: Int = 600) -> [Stop] {
        guard stops.count >= 2 else { return stops }
        let perPair = max(1, min(12, budget / max(1, stops.count - 1)))
        // The per-10° hue densification only applies while the list is
        // short (a plain 2–16 stop gradient); a repeating lattice with
        // dozens of copies keeps the budgeted count so the stop total
        // stays bounded (~600) whatever the hue sweep.
        let densify = stops.count <= 16
        var out: [Stop] = [stops[0]]
        for i in 1..<stops.count {
            let s0 = stops[i - 1], s1 = stops[i]
            let segments = densify ? segmentCount(s0, s1, interp: interp, base: perPair) : perPair
            for k in 1...segments {
                out.append(interpolate(s0, s1, t: Double(k) / Double(segments), interp: interp))
            }
        }
        return out
    }

    /// Hue-sweep-aware segment count for one pair (≥ one per 10°).
    private static func segmentCount(_ a: Stop, _ b: Stop, interp: GradientInterpolation, base: Int) -> Int {
        guard let h = GradientColorMath.hueIndex(interp.space) else { return base }
        let ca = comps(GradientColorMath.fromSrgb((a.r, a.g, a.b), to: interp.space))
        let cb = comps(GradientColorMath.fromSrgb((b.r, b.g, b.b), to: interp.space))
        // A powerless endpoint adopts the other hue (as interpolate does),
        // so its sweep is measured on the carried pair.
        let pa = GradientColorMath.hueIsPowerless((ca[0], ca[1], ca[2]), in: interp.space)
        let pb = GradientColorMath.hueIsPowerless((cb[0], cb[1], cb[2]), in: interp.space)
        let h0 = pa && !pb ? cb[h] : ca[h]
        let h1 = pb && !pa ? ca[h] : cb[h]
        let (s, e) = hueArc(h0, h1, method: interp.hue)
        return max(base, Int(ceil(abs(e - s) / 10)))
    }
}
