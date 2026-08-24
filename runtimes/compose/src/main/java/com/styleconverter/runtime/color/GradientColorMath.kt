package com.styleconverter.runtime.color

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// GradientColorMath — wave 47, lane Z1 (byte-parallel twin of iOS
// StyleEngine/background/GradientColorMath.swift; keep the constants and
// formulas in lockstep — a one-digit drift shows as a cross-platform
// colour seam in the css-images hue-family cells).
//
// PURE colour-space conversions for gradient interpolation
// (css-color-4 §12 "Interpolation"). Every stop arrives as resolved
// sRGB (the converter normalises all colours to sRGB floats —
// schema/spec/02-values.md), so the interpolation space is reached by
// converting OUT of sRGB, lerping, and converting BACK. The matrices
// and curves are the css-color-4 sample-code constants (Appendix A /
// "Sample code for color conversions"): sRGB transfer function, the
// sRGB↔XYZ-D65 pair, Bradford D65↔D50 for CIE Lab, and the OKLab LMS
// matrices from Björn Ottosson's reference implementation that the spec
// adopts (§9.2). Split from the stop resolver so plain JUnit pins the
// round trips in isolation (GradientStopResolverTest).
//
// Out-of-gamut results (an oklch hue sweep at chroma 0.295 leaves sRGB)
// are CLIPPED per channel here — the spec's CSS gamut-mapping algorithm
// (css-color-4 §13) is a follow-up on BOTH natives; clipping is what
// the frozen Chromium reference pixels for the powerless-hue tests are
// closest to and is the historical browser behaviour for gradient ramps.

/** Namespace — a colour is a 3-vector of components in SOME space plus
 *  alpha; the caller tracks which space, and the polar spaces keep hue
 *  at the index [hueIndex] reports (hsl/hwb: 0; lch/oklch: 2). */
internal object GradientColorMath {

    /**
     * Three components in an interpolation space. Polar spaces use the
     * css-color-4 channel order: hsl (h,s,l), hwb (h,w,b), lch (L,C,h),
     * oklch (L,C,h). Percent-scaled channels follow the spec's reference
     * ranges (hsl s/l 0…100, lab L 0…100, oklab L 0…1). Doubles, like
     * the iOS twin — the hue-family reference pins need the precision.
     */
    data class Triple3(val c0: Double, val c1: Double, val c2: Double)

    /** Which component index holds the hue for a polar space (null for
     *  rectangular spaces) — css-color-4 §12.5 interpolates hue as an
     *  angle, never premultiplied. */
    fun hueIndex(space: GradientInterpolation.Space): Int? = when (space) {
        GradientInterpolation.Space.HSL, GradientInterpolation.Space.HWB -> 0
        GradientInterpolation.Space.LCH, GradientInterpolation.Space.OKLCH -> 2
        else -> null
    }

    // ── sRGB transfer (css-color-4 §10.2 sample code) ────────────────────

    /** Gamma-encoded sRGB → linear light. Sign-preserving so slightly
     *  out-of-range intermediates (oklab round trips) stay continuous. */
    fun srgbToLinear(c: Double): Double {
        val s = if (c < 0) -1.0 else 1.0
        val a = abs(c)
        return if (a <= 0.04045) c / 12.92 else s * ((a + 0.055) / 1.055).pow(2.4)
    }

    /** Linear light → gamma-encoded sRGB (inverse of the above). */
    fun linearToSrgb(c: Double): Double {
        val s = if (c < 0) -1.0 else 1.0
        val a = abs(c)
        return if (a <= 0.0031308) c * 12.92 else s * (1.055 * a.pow(1 / 2.4) - 0.055)
    }

    // ── Matrices ─────────────────────────────────────────────────────────

    /** 3×3 row-major multiply. */
    private fun mul(m: Array<DoubleArray>, v: Triple3) = Triple3(
        m[0][0] * v.c0 + m[0][1] * v.c1 + m[0][2] * v.c2,
        m[1][0] * v.c0 + m[1][1] * v.c1 + m[1][2] * v.c2,
        m[2][0] * v.c0 + m[2][1] * v.c1 + m[2][2] * v.c2
    )

    /** Linear sRGB → XYZ D65 (css-color-4 sample code `lin_sRGB_to_XYZ`). */
    private val linToXYZ = arrayOf(
        doubleArrayOf(0.41239079926595934, 0.357584339383878, 0.1804807884018343),
        doubleArrayOf(0.21263900587151027, 0.715168678767756, 0.07219231536073371),
        doubleArrayOf(0.01933081871559182, 0.11919477979462598, 0.9505321522496607))
    /** XYZ D65 → linear sRGB (inverse). */
    private val xyzToLin = arrayOf(
        doubleArrayOf(3.2409699419045226, -1.537383177570094, -0.4986107602930034),
        doubleArrayOf(-0.9692436362808796, 1.8759675015077202, 0.04155505740717559),
        doubleArrayOf(0.05563007969699366, -0.20397695888897652, 1.0569715142428786))
    /** Bradford D65 → D50 (css-color-4 `D65_to_D50`) — CIE Lab is D50. */
    private val d65ToD50 = arrayOf(
        doubleArrayOf(1.0479298208405488, 0.022946793341019088, -0.05019222954313557),
        doubleArrayOf(0.029627815688159344, 0.990434484573249, -0.01707382502938514),
        doubleArrayOf(-0.009243058152591178, 0.015055144896577895, 0.7518742899580008))
    /** Bradford D50 → D65 (inverse). */
    private val d50ToD65 = arrayOf(
        doubleArrayOf(0.9554734527042182, -0.023098536874261423, 0.0632593086610217),
        doubleArrayOf(-0.028369706963208136, 1.0099954580058226, 0.021041398966943008),
        doubleArrayOf(0.012314001688319899, -0.020507696433477912, 1.3303659366080753))
    /** OKLab: XYZ D65 → LMS, LMS′ → Lab, and their inverses (§9.2). */
    private val xyzToLMS = arrayOf(
        doubleArrayOf(0.8190224379967030, 0.3619062600528904, -0.1288737815209879),
        doubleArrayOf(0.0329836539323885, 0.9292868615863434, 0.0361446663126290),
        doubleArrayOf(0.0481771893596242, 0.2642395317527308, 0.6335478284694309))
    private val lmsToLab = arrayOf(
        doubleArrayOf(0.2104542683093140, 0.7936177747023054, -0.0040720430116193),
        doubleArrayOf(1.9779985324311684, -2.4285922420485799, 0.4505937096174110),
        doubleArrayOf(0.0259040424655478, 0.7827717124575296, -0.8086757549230774))
    private val labToLMS = arrayOf(
        doubleArrayOf(1.0, 0.3963377773761749, 0.2158037573099136),
        doubleArrayOf(1.0, -0.1055613458156586, -0.0638541728258133),
        doubleArrayOf(1.0, -0.0894841775298119, -1.2914855480194092))
    private val lmsToXYZ = arrayOf(
        doubleArrayOf(1.2268798758459243, -0.5578149944602171, 0.2813910456659647),
        doubleArrayOf(-0.0405757452148008, 1.1122868032803170, -0.0717110580655164),
        doubleArrayOf(-0.0763729366746601, -0.4214933324022432, 1.5869240198367816))

    // ── CIE Lab (css-color-4 §9.1 sample code, D50) ──────────────────────

    /** D50 reference white (css-color-4 `D50 = [0.3457/0.3585, 1, …]`). */
    private val d50White = Triple3(0.3457 / 0.3585, 1.0, (1.0 - 0.3457 - 0.3585) / 0.3585)
    private const val LAB_EPS = 216.0 / 24389.0
    private const val LAB_KAPPA = 24389.0 / 27.0

    private fun xyzD50ToLab(v: Triple3): Triple3 {
        // §9.1 forward transfer: cube root above ε, linear segment below.
        fun f(t: Double) = if (t > LAB_EPS) cbrt(t) else (LAB_KAPPA * t + 16) / 116
        val fx = f(v.c0 / d50White.c0); val fy = f(v.c1 / d50White.c1); val fz = f(v.c2 / d50White.c2)
        return Triple3(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun labToXYZD50(lab: Triple3): Triple3 {
        // §9.1 inverse — the same ε/κ split, per component.
        val fy = (lab.c0 + 16) / 116; val fx = lab.c1 / 500 + fy; val fz = fy - lab.c2 / 200
        val x = if (fx.pow(3) > LAB_EPS) fx.pow(3) else (116 * fx - 16) / LAB_KAPPA
        val y = if (lab.c0 > LAB_KAPPA * LAB_EPS) ((lab.c0 + 16) / 116).pow(3) else lab.c0 / LAB_KAPPA
        val z = if (fz.pow(3) > LAB_EPS) fz.pow(3) else (116 * fz - 16) / LAB_KAPPA
        return Triple3(x * d50White.c0, y * d50White.c1, z * d50White.c2)
    }

    // ── OKLab (§9.2) ─────────────────────────────────────────────────────

    private fun xyzToOKLab(v: Triple3): Triple3 {
        val lms = mul(xyzToLMS, v)
        return mul(lmsToLab, Triple3(cbrt(lms.c0), cbrt(lms.c1), cbrt(lms.c2)))
    }

    private fun okLabToXYZ(lab: Triple3): Triple3 {
        val l = mul(labToLMS, lab)
        return mul(lmsToXYZ, Triple3(l.c0.pow(3), l.c1.pow(3), l.c2.pow(3)))
    }

    // ── Polar helpers (§7.1 HSL, §8.1 HWB, §9.3 LCH) ─────────────────────

    /** Lab-like (L, a, b) → (L, C, h) with h in [0, 360). */
    private fun toPolar(v: Triple3): Triple3 {
        var h = atan2(v.c2, v.c1) * 180 / Math.PI
        if (h < 0) h += 360
        return Triple3(v.c0, hypot(v.c1, v.c2), h)
    }

    /** (L, C, h) → (L, a, b). */
    private fun fromPolar(v: Triple3): Triple3 {
        val r = v.c2 * Math.PI / 180
        return Triple3(v.c0, v.c1 * cos(r), v.c1 * sin(r))
    }

    /**
     * sRGB → HSL per the css-color-4 §7.1 sample algorithm (h deg,
     * s/l in 0…100). Achromatic input yields s = 0 exactly, which the
     * powerless analysis relies on.
     */
    fun srgbToHSL(r: Double, g: Double, b: Double): Triple3 {
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        val l = (mx + mn) / 2
        var h = 0.0; var s = 0.0
        if (d != 0.0) {
            s = if (l == 0.0 || l == 1.0) 0.0 else (mx - l) / min(l, 1 - l)
            h = when (mx) {
                r -> (g - b) / d + (if (g < b) 6 else 0)
                g -> (b - r) / d + 2
                else -> (r - g) / d + 4
            }
            h *= 60
        }
        return Triple3(h, s * 100, l * 100)
    }

    /** HSL → sRGB (css-color-4 §7.1 `hslToRgb`). */
    fun hslToSrgb(h: Double, s: Double, l: Double): Triple3 {
        val hh = ((h % 360) + 360) % 360
        val sat = s / 100; val lig = l / 100
        val a = sat * min(lig, 1 - lig)
        // The spec's f(n) form: lightness minus a wedge of the hue circle.
        fun f(n: Double): Double {
            val k = (n + hh / 30) % 12
            return lig - a * max(-1.0, min(k - 3, min(9 - k, 1.0)))
        }
        return Triple3(f(0.0), f(8.0), f(4.0))
    }

    /** sRGB → HWB (§8.1): hue as HSL, w = min, b = 1 − max (0…100). */
    fun srgbToHWB(r: Double, g: Double, b: Double): Triple3 {
        val h = srgbToHSL(r, g, b).c0
        return Triple3(h, min(r, min(g, b)) * 100, (1 - max(r, max(g, b))) * 100)
    }

    /** HWB → sRGB (§8.1 `hwbToRgb`): w + b ≥ 100 is the achromatic gray. */
    fun hwbToSrgb(h: Double, w: Double, b: Double): Triple3 {
        val ww = w / 100; val bb = b / 100
        if (ww + bb >= 1) { val g = ww / (ww + bb); return Triple3(g, g, g) }
        // Otherwise the pure hue, mixed toward white by w and black by b.
        val base = hslToSrgb(h, 100.0, 50.0)
        fun mix(c: Double) = c * (1 - ww - bb) + ww
        return Triple3(mix(base.c0), mix(base.c1), mix(base.c2))
    }

    // ── Public: sRGB ↔ interpolation space ───────────────────────────────

    /**
     * sRGB (0…1 floats) → the requested space. LEGACY and SRGB are the
     * identity (the legacy ramp lerps sRGB components too — the
     * difference between them is premultiplication, handled by the ramp,
     * not a conversion).
     */
    fun fromSrgb(rgb: Triple3, to: GradientInterpolation.Space): Triple3 {
        when (to) {
            GradientInterpolation.Space.LEGACY, GradientInterpolation.Space.SRGB -> return rgb
            GradientInterpolation.Space.HSL -> return srgbToHSL(rgb.c0, rgb.c1, rgb.c2)
            GradientInterpolation.Space.HWB -> return srgbToHWB(rgb.c0, rgb.c1, rgb.c2)
            else -> { /* fall through to the linear-light pipeline below */ }
        }
        val lin = Triple3(srgbToLinear(rgb.c0), srgbToLinear(rgb.c1), srgbToLinear(rgb.c2))
        if (to == GradientInterpolation.Space.SRGB_LINEAR) return lin
        val xyz = mul(linToXYZ, lin)
        return when (to) {
            GradientInterpolation.Space.XYZ_D65 -> xyz
            GradientInterpolation.Space.XYZ_D50 -> mul(d65ToD50, xyz)
            GradientInterpolation.Space.LAB -> xyzD50ToLab(mul(d65ToD50, xyz))
            GradientInterpolation.Space.LCH -> toPolar(xyzD50ToLab(mul(d65ToD50, xyz)))
            GradientInterpolation.Space.OKLAB -> xyzToOKLab(xyz)
            GradientInterpolation.Space.OKLCH -> toPolar(xyzToOKLab(xyz))
            else -> rgb // unreachable — every case handled above
        }
    }

    /** Interpolation space → sRGB, clipped to 0…1 per channel (header). */
    fun toSrgb(v: Triple3, from: GradientInterpolation.Space): Triple3 {
        fun clip(c: Triple3) = Triple3(
            min(max(c.c0, 0.0), 1.0), min(max(c.c1, 0.0), 1.0), min(max(c.c2, 0.0), 1.0))
        when (from) {
            GradientInterpolation.Space.LEGACY, GradientInterpolation.Space.SRGB -> return clip(v)
            GradientInterpolation.Space.HSL -> return clip(hslToSrgb(v.c0, v.c1, v.c2))
            GradientInterpolation.Space.HWB -> return clip(hwbToSrgb(v.c0, v.c1, v.c2))
            else -> { /* fall through to the linear-light pipeline below */ }
        }
        val xyz: Triple3 = when (from) {
            GradientInterpolation.Space.SRGB_LINEAR ->
                return clip(Triple3(linearToSrgb(v.c0), linearToSrgb(v.c1), linearToSrgb(v.c2)))
            GradientInterpolation.Space.XYZ_D65 -> v
            GradientInterpolation.Space.XYZ_D50 -> mul(d50ToD65, v)
            GradientInterpolation.Space.LAB -> mul(d50ToD65, labToXYZD50(v))
            GradientInterpolation.Space.LCH -> mul(d50ToD65, labToXYZD50(fromPolar(v)))
            GradientInterpolation.Space.OKLAB -> okLabToXYZ(v)
            GradientInterpolation.Space.OKLCH -> okLabToXYZ(fromPolar(v))
            else -> v // unreachable — every case handled above
        }
        val lin = mul(xyzToLin, xyz)
        return clip(Triple3(linearToSrgb(lin.c0), linearToSrgb(lin.c1), linearToSrgb(lin.c2)))
    }

    /**
     * css-color-4 §4.4 / §7.1 / §8.1 / §9.3 powerless-hue analysis in
     * the converted colour: hsl with zero saturation, hwb with
     * w + b ≥ 100, (ok)lch with zero chroma. Powerless components are
     * treated as MISSING for interpolation (§12.2) and carried from the
     * other stop — that is what makes `red → black in hsl longer hue`
     * sweep the whole wheel (WPT gradient-longer-hue-hsl-013). The
     * chroma epsilons absorb float noise from the sRGB round trip
     * (white converts to c ≈ 1e-8 in oklch, not exactly 0) — SAME
     * values as the iOS twin.
     */
    fun hueIsPowerless(v: Triple3, space: GradientInterpolation.Space): Boolean = when (space) {
        GradientInterpolation.Space.HSL -> v.c1 <= 1e-9
        GradientInterpolation.Space.HWB -> v.c1 + v.c2 >= 100 - 1e-9
        GradientInterpolation.Space.LCH -> v.c1 < 0.02   // C on a 0…150 scale
        GradientInterpolation.Space.OKLCH -> v.c1 < 2e-4 // C on a 0…0.4 scale
        else -> false
    }
}
