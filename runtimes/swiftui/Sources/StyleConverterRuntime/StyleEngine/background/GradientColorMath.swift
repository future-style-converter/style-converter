//
//  GradientColorMath.swift
//  StyleEngine/background — wave 46, lane Y2 (css-images natives).
//
//  PURE colour-space conversions for gradient interpolation
//  (css-color-4 §13 "Interpolation"). Every stop arrives as resolved
//  sRGB (the converter normalises all colours to sRGB floats —
//  schema/spec/02-values.md), so the interpolation space is reached by
//  converting OUT of sRGB, lerping, and converting BACK. The matrices
//  and curves are the css-color-4 sample-code constants (Appendix A /
//  "Sample code for color conversions"): sRGB transfer function, the
//  sRGB↔XYZ-D65 pair, Bradford D65↔D50 for CIE Lab, and the
//  OKLab LMS matrices from Björn Ottosson's reference implementation
//  that the spec adopts (§9.2). Split from the stop resolver so XCTest
//  pins the round trips in isolation.
//
//  Out-of-gamut results (an oklch hue sweep at chroma 0.295 leaves
//  sRGB) are CLIPPED per channel here — the spec's CSS gamut-mapping
//  algorithm (css-color-4 §13) is a follow-up; clipping is what the
//  wave-45 reference pixels for the powerless-hue tests are closest to
//  and is the historical browser behaviour for gradient ramps.
//

import Foundation

/// Namespace — a colour is a 3-vector of components in SOME space plus
/// alpha; `space` says which, and the polar spaces keep hue in
/// component index 2 (hsl/hwb: index 0) — see `hueIndex`.
enum GradientColorMath {

    /// Three components in an interpolation space. Polar spaces use the
    /// css-color-4 channel order: hsl (h,s,l), hwb (h,w,b), lch (L,C,h),
    /// oklch (L,C,h). Percent-scaled channels follow the spec's
    /// reference ranges (hsl s/l 0…100, lab L 0…100, oklab L 0…1).
    typealias Triple = (Double, Double, Double)

    /// Which component index holds the hue for a polar space (nil for
    /// rectangular spaces) — css-color-4 §13.5 interpolates hue as an
    /// angle, never premultiplied.
    static func hueIndex(_ space: GradientInterpolation.Space) -> Int? {
        switch space {
        case .hsl, .hwb:  return 0
        case .lch, .oklch: return 2
        default:          return nil
        }
    }

    // MARK: - sRGB transfer (css-color-4 §10.2 sample code)

    /// Gamma-encoded sRGB → linear light. Sign-preserving so slightly
    /// out-of-range intermediates (oklab round trips) stay continuous.
    static func srgbToLinear(_ c: Double) -> Double {
        let s: Double = c < 0 ? -1 : 1, a = abs(c)
        return a <= 0.04045 ? c / 12.92 : s * pow((a + 0.055) / 1.055, 2.4)
    }

    /// Linear light → gamma-encoded sRGB (inverse of the above).
    static func linearToSrgb(_ c: Double) -> Double {
        let s: Double = c < 0 ? -1 : 1, a = abs(c)
        return a <= 0.0031308 ? c * 12.92 : s * (1.055 * pow(a, 1 / 2.4) - 0.055)
    }

    // MARK: - Matrices

    /// 3×3 row-major multiply.
    private static func mul(_ m: [[Double]], _ v: Triple) -> Triple {
        (m[0][0] * v.0 + m[0][1] * v.1 + m[0][2] * v.2,
         m[1][0] * v.0 + m[1][1] * v.1 + m[1][2] * v.2,
         m[2][0] * v.0 + m[2][1] * v.1 + m[2][2] * v.2)
    }

    /// Linear sRGB → XYZ D65 (css-color-4 sample code `lin_sRGB_to_XYZ`).
    private static let linToXYZ: [[Double]] = [
        [0.41239079926595934, 0.357584339383878,   0.1804807884018343],
        [0.21263900587151027, 0.715168678767756,   0.07219231536073371],
        [0.01933081871559182, 0.11919477979462598, 0.9505321522496607]]
    /// XYZ D65 → linear sRGB (inverse).
    private static let xyzToLin: [[Double]] = [
        [ 3.2409699419045226, -1.537383177570094,  -0.4986107602930034],
        [-0.9692436362808796,  1.8759675015077202,  0.04155505740717559],
        [ 0.05563007969699366, -0.20397695888897652, 1.0569715142428786]]
    /// Bradford D65 → D50 (css-color-4 `D65_to_D50`) — CIE Lab is D50.
    private static let d65ToD50: [[Double]] = [
        [ 1.0479298208405488,  0.022946793341019088, -0.05019222954313557],
        [ 0.029627815688159344, 0.990434484573249,   -0.01707382502938514],
        [-0.009243058152591178, 0.015055144896577895, 0.7518742899580008]]
    /// Bradford D50 → D65 (inverse).
    private static let d50ToD65: [[Double]] = [
        [ 0.9554734527042182,  -0.023098536874261423, 0.0632593086610217],
        [-0.028369706963208136, 1.0099954580058226,   0.021041398966943008],
        [ 0.012314001688319899, -0.020507696433477912, 1.3303659366080753]]
    /// OKLab: XYZ D65 → LMS, LMS′ → Lab, and their inverses (§9.2).
    private static let xyzToLMS: [[Double]] = [
        [0.8190224379967030, 0.3619062600528904, -0.1288737815209879],
        [0.0329836539323885, 0.9292868615863434,  0.0361446663126290],
        [0.0481771893596242, 0.2642395317527308,  0.6335478284694309]]
    private static let lmsToLab: [[Double]] = [
        [0.2104542683093140,  0.7936177747023054, -0.0040720430116193],
        [1.9779985324311684, -2.4285922420485799,  0.4505937096174110],
        [0.0259040424655478,  0.7827717124575296, -0.8086757549230774]]
    private static let labToLMS: [[Double]] = [
        [1.0,  0.3963377773761749,  0.2158037573099136],
        [1.0, -0.1055613458156586, -0.0638541728258133],
        [1.0, -0.0894841775298119, -1.2914855480194092]]
    private static let lmsToXYZ: [[Double]] = [
        [ 1.2268798758459243, -0.5578149944602171,  0.2813910456659647],
        [-0.0405757452148008,  1.1122868032803170, -0.0717110580655164],
        [-0.0763729366746601, -0.4214933324022432,  1.5869240198367816]]

    // MARK: - CIE Lab (css-color-4 §9.1 sample code, D50)

    /// D50 reference white (css-color-4 `D50 = [0.3457/0.3585, 1, …]`).
    private static let d50White: Triple = (0.3457 / 0.3585, 1.0, (1.0 - 0.3457 - 0.3585) / 0.3585)
    private static let labEps = 216.0 / 24389.0, labKappa = 24389.0 / 27.0

    private static func xyzD50ToLab(_ v: Triple) -> Triple {
        func f(_ t: Double) -> Double { t > labEps ? cbrt(t) : (labKappa * t + 16) / 116 }
        let fx = f(v.0 / d50White.0), fy = f(v.1 / d50White.1), fz = f(v.2 / d50White.2)
        return (116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private static func labToXYZD50(_ lab: Triple) -> Triple {
        let fy = (lab.0 + 16) / 116, fx = lab.1 / 500 + fy, fz = fy - lab.2 / 200
        let x = pow(fx, 3) > labEps ? pow(fx, 3) : (116 * fx - 16) / labKappa
        let y = lab.0 > labKappa * labEps ? pow((lab.0 + 16) / 116, 3) : lab.0 / labKappa
        let z = pow(fz, 3) > labEps ? pow(fz, 3) : (116 * fz - 16) / labKappa
        return (x * d50White.0, y * d50White.1, z * d50White.2)
    }

    // MARK: - OKLab (§9.2)

    private static func xyzToOKLab(_ v: Triple) -> Triple {
        let lms = mul(xyzToLMS, v)
        return mul(lmsToLab, (cbrt(lms.0), cbrt(lms.1), cbrt(lms.2)))
    }

    private static func okLabToXYZ(_ lab: Triple) -> Triple {
        let l = mul(labToLMS, lab)
        return mul(lmsToXYZ, (pow(l.0, 3), pow(l.1, 3), pow(l.2, 3)))
    }

    // MARK: - Polar helpers (§7.1 HSL, §8.1 HWB, §9.3 LCH)

    /// Lab-like (L, a, b) → (L, C, h) with h in [0, 360).
    private static func toPolar(_ v: Triple) -> Triple {
        var h = atan2(v.2, v.1) * 180 / .pi
        if h < 0 { h += 360 }
        return (v.0, hypot(v.1, v.2), h)
    }

    /// (L, C, h) → (L, a, b).
    private static func fromPolar(_ v: Triple) -> Triple {
        let r = v.2 * .pi / 180
        return (v.0, v.1 * cos(r), v.1 * sin(r))
    }

    /// sRGB → HSL per the css-color-4 §7.1 sample algorithm (h deg,
    /// s/l in 0…100). Achromatic input yields s = 0 exactly, which the
    /// powerless analysis relies on.
    static func srgbToHSL(_ r: Double, _ g: Double, _ b: Double) -> Triple {
        let mx = max(r, g, b), mn = min(r, g, b), d = mx - mn
        let l = (mx + mn) / 2
        var h = 0.0, s = 0.0
        if d != 0 {
            s = (l == 0 || l == 1) ? 0 : (mx - l) / min(l, 1 - l)
            if mx == r { h = (g - b) / d + (g < b ? 6 : 0) }
            else if mx == g { h = (b - r) / d + 2 }
            else { h = (r - g) / d + 4 }
            h *= 60
        }
        return (h, s * 100, l * 100)
    }

    /// HSL → sRGB (css-color-4 §7.1 `hslToRgb`).
    static func hslToSrgb(_ h: Double, _ s: Double, _ l: Double) -> Triple {
        let hh = ((h.truncatingRemainder(dividingBy: 360)) + 360).truncatingRemainder(dividingBy: 360)
        let sat = s / 100, lig = l / 100
        let a = sat * min(lig, 1 - lig)
        func f(_ n: Double) -> Double {
            let k = (n + hh / 30).truncatingRemainder(dividingBy: 12)
            return lig - a * max(-1, min(k - 3, 9 - k, 1))
        }
        return (f(0), f(8), f(4))
    }

    /// sRGB → HWB (§8.1): hue as HSL, w = min, b = 1 − max (0…100).
    static func srgbToHWB(_ r: Double, _ g: Double, _ b: Double) -> Triple {
        let h = srgbToHSL(r, g, b).0
        return (h, min(r, g, b) * 100, (1 - max(r, g, b)) * 100)
    }

    /// HWB → sRGB (§8.1 `hwbToRgb`): w + b ≥ 100 is the achromatic gray.
    static func hwbToSrgb(_ h: Double, _ w: Double, _ b: Double) -> Triple {
        let ww = w / 100, bb = b / 100
        if ww + bb >= 1 { let g = ww / (ww + bb); return (g, g, g) }
        // Otherwise the pure hue, mixed toward white by w and black by b.
        let base = hslToSrgb(h, 100, 50)
        func mix(_ c: Double) -> Double { c * (1 - ww - bb) + ww }
        return (mix(base.0), mix(base.1), mix(base.2))
    }

    // MARK: - Public: sRGB ↔ interpolation space

    /// sRGB (0…1 floats) → the requested space. `.legacy` and `.srgb`
    /// are the identity (the legacy ramp lerps sRGB components too —
    /// the difference between them is premultiplication, handled by the
    /// resolver, not a conversion).
    static func fromSrgb(_ rgb: Triple, to space: GradientInterpolation.Space) -> Triple {
        switch space {
        case .legacy, .srgb: return rgb
        case .hsl: return srgbToHSL(rgb.0, rgb.1, rgb.2)
        case .hwb: return srgbToHWB(rgb.0, rgb.1, rgb.2)
        default: break
        }
        let lin = (srgbToLinear(rgb.0), srgbToLinear(rgb.1), srgbToLinear(rgb.2))
        if space == .srgbLinear { return lin }
        let xyz = mul(linToXYZ, lin)
        switch space {
        case .xyzD65: return xyz
        case .xyzD50: return mul(d65ToD50, xyz)
        case .lab:    return xyzD50ToLab(mul(d65ToD50, xyz))
        case .lch:    return toPolar(xyzD50ToLab(mul(d65ToD50, xyz)))
        case .oklab:  return xyzToOKLab(xyz)
        case .oklch:  return toPolar(xyzToOKLab(xyz))
        default:      return rgb // unreachable — every case handled above
        }
    }

    /// Interpolation space → sRGB, clipped to 0…1 per channel (header).
    static func toSrgb(_ v: Triple, from space: GradientInterpolation.Space) -> Triple {
        func clip(_ c: Triple) -> Triple { (min(max(c.0, 0), 1), min(max(c.1, 0), 1), min(max(c.2, 0), 1)) }
        switch space {
        case .legacy, .srgb: return clip(v)
        case .hsl: return clip(hslToSrgb(v.0, v.1, v.2))
        case .hwb: return clip(hwbToSrgb(v.0, v.1, v.2))
        default: break
        }
        let xyz: Triple
        switch space {
        case .srgbLinear: return clip((linearToSrgb(v.0), linearToSrgb(v.1), linearToSrgb(v.2)))
        case .xyzD65: xyz = v
        case .xyzD50: xyz = mul(d50ToD65, v)
        case .lab:    xyz = mul(d50ToD65, labToXYZD50(v))
        case .lch:    xyz = mul(d50ToD65, labToXYZD50(fromPolar(v)))
        case .oklab:  xyz = okLabToXYZ(v)
        case .oklch:  xyz = okLabToXYZ(fromPolar(v))
        default:      xyz = v // unreachable — every case handled above
        }
        let lin = mul(xyzToLin, xyz)
        return clip((linearToSrgb(lin.0), linearToSrgb(lin.1), linearToSrgb(lin.2)))
    }

    /// css-color-4 §4.4 / §7.1 / §8.1 / §9.3 powerless-hue analysis in
    /// the converted colour: hsl with zero saturation, hwb with
    /// w + b ≥ 100, (ok)lch with zero chroma. Powerless components are
    /// treated as MISSING for interpolation (§13.3) and carried from the
    /// other stop — that is what makes `red → black in hsl longer hue`
    /// sweep the whole wheel (WPT gradient-longer-hue-hsl-013). The
    /// chroma epsilons absorb float noise from the sRGB round trip
    /// (white converts to c ≈ 1e-8 in oklch, not exactly 0).
    static func hueIsPowerless(_ v: Triple, in space: GradientInterpolation.Space) -> Bool {
        switch space {
        case .hsl:   return v.1 <= 1e-9
        case .hwb:   return v.1 + v.2 >= 100 - 1e-9
        case .lch:   return v.1 < 0.02   // C on a 0…150 scale
        case .oklch: return v.1 < 2e-4   // C on a 0…0.4 scale
        default:     return false
        }
    }
}
