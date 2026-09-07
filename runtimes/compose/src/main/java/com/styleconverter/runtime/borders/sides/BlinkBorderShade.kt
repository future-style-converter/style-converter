package com.styleconverter.runtime.borders.sides

// Compose Color is pure Kotlin (packed-ULong channels), so this whole file
// runs in the JVM unit suite — the palette is pinned there
// (BorderShadeBlinkGateTest / BorderFidelityWave3Test /
// BorderFidelityWave4Test — retro P2e corrected the last name, which read
// `Wave4Test`, a class that has never existed).
import androidx.compose.ui.graphics.Color

/**
 * Blink's two-tone palette for the 3D border styles (groove/ridge/inset/
 * outset) — the ONE Compose implementation (retro R6, audit finding A7#2),
 * byte-parallel with the iOS twin
 * `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/borders/sides/
 * BlinkBorderShade.swift` (landed in the 2026-09 retro by R6's seam patch
 * 03; iOS's `BorderSideApplier.swift` `shade(_:light:)` delegates to it).
 * Retro P2e had corrected an EARLIER version of this banner to "no Swift
 * file of that name exists / the port is written but UNLANDED" — true of the
 * tree before that seam applied, false after it; round-2 F1 (skeptics S3
 * defect 3, S6 defect 4) re-trued it to the integrated state. Both twins are
 * pinned with the same table (BorderShadeBlinkGateTest /
 * BordersTests.testLightBandLiftsBlinkGate). 3D shading is UA-defined
 * (css-backgrounds-3 §3.2 only names the styles), so the web reference
 * engine's own arithmetic is the cross-platform contract:
 *
 *  - DARK band  — color.cc `Color::Dark()`: v = max(r,g,b); every channel
 *    scales by the SUBTRACTIVE multiplier max(0, (v − 0.33)/v). The
 *    pre-wave-3 flat ×0.65 was a one-point fit at v = 0.937 (declared 239 →
 *    155) that drifted for mid/dark bases (#808080 darkens ×0.343 in Blink).
 *  - LIGHT band — the declared colour UNCHANGED, unless the dark band would
 *    not read against it: box_border_painter.cc `CalculateBorderStyleColor`
 *    (anonymous namespace) lightens via `Color::Light()` iff
 *    color_utils::GetContrastRatio(color, color.Dark()) <
 *    kMinimumBorderEdgeContrastRatio (1.75f) — see [lightBandLifts].
 *    Light(): pure black → the fixed kLightenedBlack rgb(84,84,84); every
 *    other base scales ADDITIVELY by min(1, v + 0.33)/v.
 *
 * Before this retro the light-band gate was "max channel ≤ 0.33" on Compose
 * and "pure black only" on Swift — the natives disagreed for every
 * 0 < v ≤ 0.33 base and NEITHER was Blink (a v = 0.33 grey is NOT lifted:
 * contrast 2.78; a dark blue (0.1, 0.1, 0.5) IS: contrast 1.38).
 *
 * THE 8-BIT INPUT CONTRACT (retro round-2: F1 here, F2 on Swift; skeptic S3
 * defect 2). Compose's sRGB `Color(r, g, b, a)` packs every channel to 8
 * bits by ROUNDING at construction, so [shade] only ever sees k/255 inputs —
 * while iOS's `UIColor(base).getRed` hands back the declared floats
 * unquantised. The pure functions agreed on every 8-bit-exact row, yet a
 * colour whose raw floats straddle the gate's boundary lifted on one native
 * and not the other (S3's 400k-sample search: light bands up to 84/255
 * apart — a 0.2137 grey: raw contrast 1.7507 ≥ 1.75, packed 54/255 → 1.7378
 * < 1.75). Both twins therefore pack their gate inputs to k/255 with the SAME
 * rule — [pack8], step 0 of [lightBandLifts] on both sides. The Swift twin
 * additionally re-packs inside its `shade` because its channel read is raw;
 * here the Color constructor has already done exactly that (stated as a
 * precondition on [shade]), so a second pack would be the identity. Every
 * corpus / fixture 3D-border colour today is hex/rgb()/named — already
 * k/255 — so no cell moves; an oklch()/hsl()/alpha-composited base is what
 * the contract protects.
 */
internal object BlinkBorderShade {

    /**
     * The band colour for [base]: the light edge when [lighten], else the
     * dark edge. Alpha is carried over untouched (Chromium shades in-gamut
     * without changing transparency). Results stay as floats: Compose packs
     * sRGB Colors to 8 bits by ROUNDING and the committed baselines/pins were
     * taken on that quantisation (Blink truncates — the ≤1-LSB difference is
     * reproduced only inside the gate, where it decides the boundary).
     *
     * PRECONDITION (the 8-bit input contract, class banner): [base] is an
     * sRGB Compose Color, whose constructor has ALREADY packed r/g/b to
     * k/255 — so the multipliers below and the gate see the same 8-bit
     * values the Swift twin's `pack8` produces from its raw read. Every
     * Color this runtime builds from the IR is sRGB (`Color(r, g, b, a)` /
     * `Color(argb)`); a Float16-stored non-sRGB colour space would break the
     * precondition and would need [pack8] applied here too.
     */
    fun shade(base: Color, lighten: Boolean): Color {
        // Blink's brightness proxy: the value channel v = max(r,g,b).
        val v = maxOf(base.red, base.green, base.blue)
        if (lighten) {
            // CalculateBorderStyleColor: the light edge keeps the declared
            // colour unless it fails the contrast gate against Dark().
            if (!lightBandLifts(base.red, base.green, base.blue)) return base
            // Color::Light(): pure black lifts to the fixed kLightenedBlack
            // rgb(84,84,84) (alpha carried over)…
            if (v <= 0f) return Color(84 / 255f, 84 / 255f, 84 / 255f, base.alpha)
            // …and every other base scales ADDITIVELY by min(1, v+0.33)/v
            // (the min keeps the max channel in gamut; the others are ≤ v
            // so they stay in gamut too).
            val lightMultiplier = kotlin.math.min(1f, v + 0.33f) / v
            return Color(
                red = base.red * lightMultiplier,
                green = base.green * lightMultiplier,
                blue = base.blue * lightMultiplier,
                alpha = base.alpha,
            )
        }
        // Dark band — every channel scaled by the one shared multiplier
        // (Blink darkens uniformly, preserving hue).
        val m = darkMultiplier(v)
        return Color(red = base.red * m, green = base.green * m, blue = base.blue * m, alpha = base.alpha)
    }

    /**
     * Color::Dark()'s per-channel multiplier for a base whose max channel is
     * [v]: max(0, (v − 0.33) / v) — 0.33 of full-scale removed then
     * renormalised by v; clamps to 0 for v ≤ 0.33 and guards the v == 0
     * division exactly as color.cc does (`v == 0 ? 0 : …`).
     */
    fun darkMultiplier(v: Float): Float =
        if (v <= 0f) 0f else ((v - 0.33f) / v).coerceAtLeast(0f)

    /**
     * Compose's sRGB channel store, as a rule (the 8-bit input contract,
     * class banner): `Color(r, g, b, a)` keeps `((c · 255) + 0.5).toInt()`
     * per channel — round-half-up — and `Color.red` hands back k/255.
     * Applied to the gate's inputs so a DIRECT caller with raw floats (the
     * pinned tables, or a colour that bypassed the constructor's packing)
     * gets the verdict the live path gets. `Math.round(double)` is
     * floor(x + 0.5), identical to Swift's `.rounded()` (half away from
     * zero) for c ≥ 0 — the twin's `pack8` is `(c * 255).rounded() / 255`,
     * computed in CGFloat (Double) as here, so both land on the identical
     * k/255.0. Idempotent on a packed channel (k/255f · 255 is within 1e-5
     * of k, never near a .5). Distinct from [quantizeTo8Bit] — Blink's
     * TRUNCATING store, applied only to Dark() inside the gate: two different
     * 8-bit stores, each reproduced where its owner applies it.
     */
    fun pack8(c: Float): Double = Math.round(c.toDouble() * 255.0) / 255.0

    /**
     * Blink's light-edge decision — `CalculateBorderStyleColor`, ported
     * operation for operation; the same body as the Swift twin's
     * `lightBandLifts`, both pinned with one shared table
     * (BorderShadeBlinkGateTest / BordersTests.testLightBandLiftsBlinkGate).
     *
     * Returns true when the light edge is lightened via Color::Light():
     *   0. the 8-bit input contract — every channel is [pack8]ed first, so a
     *      raw-float caller and the packed live path agree (the flip rows in
     *      the shared table: a 0.2137 grey lifts ONLY packed, S3's
     *      (0.082051, 0.237704, 0.218751) lifts ONLY raw);
     *   1. Blink's early-out — `color.Red() >= 150 || color.Green() >= 92` is
     *      never lightened (the literal code path, a brute-force-derived
     *      bound on the gate below — crrev.com/c/4200827; reproducing it
     *      keeps the boundary identical even where float rounding differs);
     *   2. otherwise lighten iff color_utils::GetContrastRatio(color,
     *      color.Dark()) < kMinimumBorderEdgeContrastRatio (1.75f), where
     *      Dark() is 8-bit-QUANTISED first exactly as Blink stores it
     *      ([quantizeTo8Bit]) — the quantisation moves the contrast at the
     *      gate's boundary, so it is reproduced rather than approximated.
     * Pure black: contrast(black, black) = 1 < 1.75 → lifts. All arithmetic
     * in Double so the Kotlin and Swift (CGFloat) twins compute identical
     * values.
     */
    fun lightBandLifts(r: Float, g: Float, b: Float): Boolean {
        // 0. The 8-bit input contract — the same pack8 the Swift twin applies
        //    (Double from here on: k/255.0 exactly, on both sides).
        val r8 = pack8(r)
        val g8 = pack8(g)
        val b8 = pack8(b)
        // 1. The early-out (8-bit channels: 150/255, 92/255).
        if (r8 >= 150.0 / 255.0 || g8 >= 92.0 / 255.0) return false
        // 2. Color::Dark() of the base, 8-bit-quantised. The multiplier is
        //    recomputed in Double here (not [darkMultiplier]'s Float) so the
        //    Swift twin — CGFloat is Double — lands on the identical value
        //    before the truncating quantisation decides a boundary colour.
        val v = maxOf(r8, g8, b8)
        val m = if (v <= 0.0) 0.0 else ((v - 0.33) / v).coerceAtLeast(0.0)
        val darkLuminance = relativeLuminance(
            quantizeTo8Bit(r8 * m), quantizeTo8Bit(g8 * m), quantizeTo8Bit(b8 * m),
        )
        val baseLuminance = relativeLuminance(r8, g8, b8)
        // color_utils::GetContrastRatio < kMinimumBorderEdgeContrastRatio.
        return contrastRatio(baseLuminance, darkLuminance) < 1.75
    }

    /**
     * Color::Dark()'s channel store: `static_cast<int>(c · nextafterf(256,
     * 0))` then /255 — TRUNCATION, not rounding. nextafterf(256, 0) is the
     * largest float below 256 (Math.nextDown(256f) here, Float(256).nextDown
     * in Swift).
     */
    fun quantizeTo8Bit(c: Double): Double =
        kotlin.math.floor(c * Math.nextDown(256f).toDouble()) / 255.0

    /**
     * ui/gfx/color_utils.cc GetRelativeLuminance4f: the WCAG relative
     * luminance 0.2126·R + 0.7152·G + 0.0722·B over Linearize(c) = c ≤
     * 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055)^2.4 — Chromium uses the
     * 0.04045 knee (its comment cites Wikipedia's sRGB transform), NOT the
     * WCAG 2.0 text's 0.03928; the twin must too.
     */
    fun relativeLuminance(r: Double, g: Double, b: Double): Double {
        fun linearize(c: Double): Double =
            if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
    }

    /**
     * ui/gfx/color_utils.cc GetContrastRatio(luminance_a, luminance_b):
     * (L_hi + 0.05) / (L_lo + 0.05) — symmetric in its arguments.
     */
    fun contrastRatio(luminanceA: Double, luminanceB: Double): Double {
        val a = luminanceA + 0.05
        val b = luminanceB + 0.05
        return if (a > b) a / b else b / a
    }
}
