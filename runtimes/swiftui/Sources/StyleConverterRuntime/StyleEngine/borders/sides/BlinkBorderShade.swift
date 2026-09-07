//
//  BlinkBorderShade.swift
//  StyleConverterRuntime — borders/sides
//
//  Blink's two-tone palette for the 3D border styles (groove/ridge/inset/
//  outset) — the ONE iOS implementation, byte-parallel with the Compose twin
//  BlinkBorderShade.kt (retro R6, audit finding A7#2). 3D shading is
//  UA-defined (css-backgrounds-3 §3.2 only names the styles), so the web
//  reference engine's own arithmetic is the cross-platform contract:
//
//   - DARK band  — color.cc `Color::Dark()`: v = max(r,g,b); every channel
//     scales by the SUBTRACTIVE multiplier max(0, (v − 0.33)/v). The old
//     flat ×0.65 was a one-point fit at v = 0.937 that drifted for mid/dark
//     bases (#808080 darkens ×0.343 in Blink).
//   - LIGHT band — the declared colour UNCHANGED, unless the dark band would
//     not read against it: box_border_painter.cc `CalculateBorderStyleColor`
//     (anonymous namespace) lightens via `Color::Light()` iff
//     color_utils::GetContrastRatio(color, color.Dark()) <
//     kMinimumBorderEdgeContrastRatio (1.75f) — see lightBandLifts.
//     Light(): pure black → the fixed kLightenedBlack rgb(84,84,84); every
//     other base scales ADDITIVELY by min(1, v + 0.33)/v.
//
//  Before this retro the light-band gate was "pure black only" here and
//  "max channel ≤ 0.33" on Compose — the natives disagreed for every
//  0 < v ≤ 0.33 base and NEITHER was Blink (a v = 0.33 grey is NOT lifted:
//  contrast 2.78; a dark blue (0.1, 0.1, 0.5) IS: contrast 1.38). Pinned with
//  the SAME table on both twins (BordersTests.testLightBandLiftsBlinkGate /
//  BorderShadeBlinkGateTest).
//
//  THE 8-BIT INPUT CONTRACT (retro round-2 lane F2, skeptic S3 defect 2):
//  Compose's sRGB `Color(r, g, b, a)` packs every channel to 8 bits by
//  ROUNDING at construction, so the Kotlin twin's live `shade` only ever
//  sees k/255 inputs — while `UIColor(base).getRed` here hands back the
//  declared floats unquantised. The pure functions agreed on every
//  8-bit-exact row, yet a colour whose raw floats straddle the gate's
//  boundary (a 0.2137 grey: raw contrast 1.7507 ≥ 1.75, packed 54/255 →
//  1.7378 < 1.75) lifted on one native and not the other — light bands up
//  to 84/255 apart. Both live paths therefore compute on k/255 inputs by
//  the SAME rule: `pack8` here, before the gate and before the Light()/
//  Dark() multipliers; on Compose `Color(r, g, b, a)` already packs at
//  construction (the explicit pack inside its `lightBandLifts`, so the
//  PURE function matches too, is round-2 lane F1's). Every corpus/fixture 3D-border
//  colour today is hex/rgb()/named — already k/255 — so no cell moves; an
//  oklch()/hsl()/alpha-composited base is what the contract protects.
//

import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

enum BlinkBorderShade {

    /// The band colour for `base`: the light edge when `light`, else the dark
    /// edge. Alpha is carried over untouched (Chromium shades in-gamut
    /// without changing transparency). UIColor bridging extracts the RGBA
    /// components (SwiftUI.Color has no channel accessors on iOS 16); a
    /// non-RGB-convertible colour (dynamic/catalog) falls back to the base
    /// rather than guessing — no silent wrong-colour band.
    static func shade(_ base: Color, light: Bool) -> Color {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        guard UIColor(base).getRed(&r, green: &g, blue: &b, alpha: &a) else { return base }
        // The 8-bit input contract (file banner): pack the three colour
        // channels to k/255 exactly as Compose's Color constructor does, so
        // the gate below AND the two multipliers see what the Kotlin twin's
        // live path sees. Alpha is only carried over, so it stays as read.
        r = pack8(r); g = pack8(g); b = pack8(b)
        // Blink's brightness proxy: the value channel v = max(r,g,b).
        let v = max(r, max(g, b))
        if light {
            // CalculateBorderStyleColor: the light edge keeps the declared
            // colour unless it fails the contrast gate against Dark().
            if !lightBandLifts(r: r, g: g, b: b) { return base }
            // Color::Light(): pure black lifts to the fixed kLightenedBlack
            // rgb(84,84,84) (alpha carried over)…
            if v <= 0 {
                return Color(red: 84.0 / 255.0, green: 84.0 / 255.0,
                             blue: 84.0 / 255.0, opacity: a)
            }
            // …and every other base scales ADDITIVELY by min(1, v+0.33)/v
            // (the min keeps the max channel in gamut; the others are ≤ v so
            // they stay in gamut too).
            let m = min(1, v + 0.33) / v
            return Color(red: r * m, green: g * m, blue: b * m, opacity: a)
        }
        // Dark band — every channel scaled by the one shared multiplier
        // (Blink darkens uniformly, preserving hue).
        let m = darkMultiplier(v)
        return Color(red: r * m, green: g * m, blue: b * m, opacity: a)
    }

    /// Color::Dark()'s per-channel multiplier for a base whose max channel is
    /// `v`: max(0, (v − 0.33) / v) — 0.33 of full-scale removed then
    /// renormalised by v; clamps to 0 for v ≤ 0.33 and guards the v == 0
    /// division exactly as color.cc does (`v == 0 ? 0 : …`).
    static func darkMultiplier(_ v: CGFloat) -> CGFloat {
        v <= 0 ? 0 : max(0, (v - 0.33) / v)
    }

    /// Compose's sRGB channel packing — `Color(r, g, b, a)` stores
    /// ((c · 255) + 0.5).toInt() per channel, i.e. round-half-up; the k/255
    /// that comes back out of `Color.red` is what the Kotlin twin's `shade`
    /// and `lightBandLifts` receive. `.rounded()` is schoolbook rounding
    /// (half away from zero), identical to that for c ≥ 0. Idempotent: a
    /// second pack of a k/255 value lands on the same k (the product k/255·255
    /// is within 1e-13 of the integer, never near a .5). Distinct from
    /// `quantizeTo8Bit` — Blink's TRUNCATING store, applied only to Dark()
    /// inside the gate: two different 8-bit stores, each reproduced where
    /// its owner applies it.
    static func pack8(_ c: CGFloat) -> CGFloat {
        (c * 255).rounded() / 255
    }

    /// Blink's light-edge decision — `CalculateBorderStyleColor`, ported
    /// operation for operation (same body as the Compose twin):
    ///   1. Blink's early-out — `color.Red() >= 150 || color.Green() >= 92`
    ///      is never lightened (the literal code path, a brute-force-derived
    ///      bound on the gate below — crrev.com/c/4200827; reproducing it
    ///      keeps the boundary identical even where float rounding differs);
    ///   2. otherwise lighten iff color_utils::GetContrastRatio(color,
    ///      color.Dark()) < kMinimumBorderEdgeContrastRatio (1.75f), where
    ///      Dark() is 8-bit-QUANTISED first exactly as Blink stores it
    ///      (quantizeTo8Bit) — the quantisation moves the contrast at the
    ///      gate's boundary, so it is reproduced rather than approximated.
    /// Pure black: contrast(black, black) = 1 < 1.75 → lifts. All arithmetic
    /// in Double (CGFloat) — the Kotlin twin computes the gate in Double too.
    /// Inputs are packed to k/255 first (the 8-bit input contract, banner):
    /// `shade` already packs, so this is the identity on the live path; it is
    /// here so the pure function gives a direct caller with raw floats (the
    /// pinned tables) the verdict the live path would — the same verdict the
    /// Kotlin twin's live path gets from its packed Color (round-2 F1 adds
    /// the matching explicit pack to the Kotlin `lightBandLifts`).
    static func lightBandLifts(r rawR: CGFloat, g rawG: CGFloat, b rawB: CGFloat) -> Bool {
        // 0. The 8-bit input contract — the same pack8 the live path applies.
        let r = pack8(rawR), g = pack8(rawG), b = pack8(rawB)
        // 1. The early-out (8-bit channels: 150/255, 92/255).
        if r >= 150.0 / 255.0 || g >= 92.0 / 255.0 { return false }
        // 2. Color::Dark() of the base, 8-bit-quantised.
        let v = Double(max(r, max(g, b)))
        let m = v <= 0.0 ? 0.0 : max(0.0, (v - 0.33) / v)
        let darkLuminance = relativeLuminance(
            quantizeTo8Bit(Double(r) * m), quantizeTo8Bit(Double(g) * m), quantizeTo8Bit(Double(b) * m))
        let baseLuminance = relativeLuminance(Double(r), Double(g), Double(b))
        // color_utils::GetContrastRatio < kMinimumBorderEdgeContrastRatio.
        return contrastRatio(baseLuminance, darkLuminance) < 1.75
    }

    /// Color::Dark()'s channel store: `static_cast<int>(c · nextafterf(256,
    /// 0))` then /255 — TRUNCATION, not rounding. nextafterf(256, 0) is the
    /// largest float below 256 (Float(256).nextDown here, Math.nextDown(256f)
    /// on the JVM).
    static func quantizeTo8Bit(_ c: Double) -> Double {
        floor(c * Double(Float(256).nextDown)) / 255.0
    }

    /// ui/gfx/color_utils.cc GetRelativeLuminance4f: the WCAG relative
    /// luminance 0.2126·R + 0.7152·G + 0.0722·B over Linearize(c) = c ≤
    /// 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055)^2.4 — Chromium uses the
    /// 0.04045 knee (its comment cites Wikipedia's sRGB transform), NOT the
    /// WCAG 2.0 text's 0.03928; the twin does too.
    static func relativeLuminance(_ r: Double, _ g: Double, _ b: Double) -> Double {
        func linearize(_ c: Double) -> Double {
            c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
    }

    /// ui/gfx/color_utils.cc GetContrastRatio(luminance_a, luminance_b):
    /// (L_hi + 0.05) / (L_lo + 0.05) — symmetric in its arguments.
    static func contrastRatio(_ luminanceA: Double, _ luminanceB: Double) -> Double {
        let a = luminanceA + 0.05
        let b = luminanceB + 0.05
        return a > b ? a / b : b / a
    }
}
