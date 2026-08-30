package app.parsing.css.properties.primitiveParsers

import app.irmodels.ColorConversion
import app.irmodels.IRColor
import app.irmodels.SRGB

/**
 * Parses CSS color values into IRColor instances.
 *
 * All static colors are normalized to sRGB during parsing for cross-platform use.
 * Dynamic colors (color-mix, light-dark, currentColor, var(), relative) have srgb=null.
 *
 * Supports:
 * - Hex: #RGB, #RGBA, #RRGGBB, #RRGGBBAA
 * - RGB/RGBA: rgb(255, 0, 0), rgba(255, 0, 0, 0.5), rgb(255 0 0), rgb(255 0 0 / 50%)
 * - HSL/HSLA: hsl(120, 100%, 50%), hsla(120, 100%, 50%, 0.5), hsl(120deg 100% 50%)
 * - HWB: hwb(120 0% 0%), hwb(120deg 0% 0% / 50%)
 * - Lab/LCH/OKLab/OKLCH: lab(50% 25 -25), oklch(0.7 0.15 180)
 * - color-mix(): color-mix(in srgb, red 50%, blue)
 * - Named colors: red, blue, transparent, currentColor
 */
object ColorParser {

    // Hex patterns: #RGB, #RGBA, #RRGGBB, #RRGGBBAA
    private val hexRegex = """^#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""".toRegex()

    // Legacy comma-separated RGB: rgb(255, 0, 0) or rgba(255, 0, 0, 0.5)
    private val rgbCommaRegex = """^rgba?\s*\(\s*([\d.]+%?|none)\s*,\s*([\d.]+%?|none)\s*,\s*([\d.]+%?|none)\s*(?:,\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // Modern space-separated RGB: rgb(255 0 0) or rgb(255 0 0 / 50%)
    private val rgbSpaceRegex = """^rgba?\s*\(\s*([\d.]+%?|none)\s+([\d.]+%?|none)\s+([\d.]+%?|none)\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // CSS <hue> for hsl()/hsla(): the hue may be a bare <number> OR an <angle>
    // with a unit (CSS Color 4 §7 "The HSL functions"). This sub-pattern captures
    // the whole hue token so grad/rad/turn units and scientific-notation numbers
    // are matched (not dropped):
    //   [+-]?                    optional sign
    //   (?:\d+\.?\d*|\.\d+)      integer/decimal mantissa (e.g. 120, 120.0, .5)
    //   (?:[eE][+-]?\d+)?        optional scientific exponent (e.g. 1.2e2 == 120)
    //   (?:deg|grad|rad|turn)?   optional <angle> unit; bare number = degrees
    // parseHslHue() below normalizes any unit to degrees via AngleParser so the
    // existing hue→rgb math (ColorConversion.hslToSrgb, which wraps mod 360) is
    // unchanged. Previously the hue group was `[\d.]+(?:deg)?`, which failed to
    // match grad/rad/turn/scientific hues and made the whole color parse null.
    private const val HUE = """[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?(?:deg|grad|rad|turn)?"""

    // Legacy comma-separated HSL: hsl(120, 100%, 50%) - supports 'none' keyword + angle-unit/sci-notation hue
    private val hslCommaRegex = """^hsla?\s*\(\s*($HUE|none)\s*,\s*([\d.]+|none)%?\s*,\s*([\d.]+|none)%?\s*(?:,\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // Modern space-separated HSL: hsl(120deg 100% 50%) or hsl(120 100% 50% / 50%) - supports 'none' keyword + angle-unit/sci-notation hue
    private val hslSpaceRegex = """^hsla?\s*\(\s*($HUE|none)\s+([\d.]+|none)%?\s+([\d.]+|none)%?\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // HWB: hwb(120 0% 0%) or hwb(120deg 0% 0% / 50%).
    // The hue group is $HUE — the same one hsl adopted — so angle units
    // (grad/rad/turn), negative hues and scientific notation match; the old
    // group `[\d.]+(?:deg)?` failed every one of those and nulled the whole
    // colour. `none` is a valid component for all three channels
    // (css-color-4 §4.1: a missing component behaves as 0), and the parse is
    // case-insensitive like every other colour function here — HWB(...) was
    // silently rejected before.
    private val hwbRegex = """^hwb\s*\(\s*($HUE|none)\s+([\d.]+%|none)\s+([\d.]+%|none)\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // Lab: lab(50% 25 -25) or lab(50% 25 -25 / 50%) - supports 'none' keyword.
    // a/b accept a <percentage> too (css-color-4 §9.1: ±100% = ±125) — the
    // old `-?[\d.]+` group nulled the whole colour for `lab(50% 100% -100%)`.
    private val labRegex = """^lab\s*\(\s*([\d.]+%?|none)\s+(-?[\d.]+%?|none)\s+(-?[\d.]+%?|none)\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // LCH: lch(50% 25 180) or lch(50% 25 180deg / 50%) - supports 'none' keyword.
    // The hue is a full <hue> ($HUE, unit INSIDE the group so parseHslHue can
    // normalise grad/rad/turn to degrees). The old `([\d.]+|none)(?:deg)?`
    // form nulled the whole colour for `-90deg`, `200grad`, `1.5rad` and
    // `0.25turn` — the unit sat OUTSIDE the capture, so any unit other than
    // a literal `deg` made the regex fail entirely.
    // wave-48 lane W5: chroma accepts a <percentage> (css-color-4 §8.2:
    // 100% = 150 for lch) and a leading `-` (negative chroma is CLAMPED to 0
    // at parsed-value time, not invalid). The old `([\d.]+|none)` group
    // nulled `lch(50% 100% 0deg)` entirely, which routed every gradient in
    // WPT gradient-{in,de}creasing-hue-lch to the Raw fallback — the web
    // runtime re-emitted the author bytes (1.0000 P) but both natives
    // painted nothing (0.7278 F, wave48-cal css-images).
    private val lchRegex = """^lch\s*\(\s*([\d.]+%?|none)\s+(-?[\d.]+%?|none)\s+($HUE|none)\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // OKLab: oklab(0.7 -0.1 0.15) or oklab(70% -0.1 0.15 / 50%) - supports
    // 'none'. a/b accept a <percentage> (css-color-4 §9.2: ±100% = ±0.4).
    private val oklabRegex = """^oklab\s*\(\s*([\d.]+%?|none)\s+(-?[\d.]+%?|none)\s+(-?[\d.]+%?|none)\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // OKLCH: oklch(0.7 0.15 180) or oklch(70% 0.15 180deg / 50%) - supports
    // 'none'. Hue is a full <hue> for the same reason as lchRegex above.
    // Chroma: <percentage> (100% = 0.4, css-color-4 §8.2) and negative-clamp,
    // same wave-48 rationale as lch above.
    private val oklchRegex = """^oklch\s*\(\s*([\d.]+%?|none)\s+(-?[\d.]+%?|none)\s+($HUE|none)\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // color-mix: color-mix(in srgb, red 50%, blue)
    private val colorMixRegex = """^color-mix\s*\(\s*in\s+(\w+)\s*,\s*(.+)\s*\)$""".toRegex()

    // color(): color(display-p3 0.9 0.5 0.3) or color(srgb 1 0.5 0.2 / 0.8)
    private val colorFunctionRegex = """^color\s*\(\s*([\w-]+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    fun parse(value: String): IRColor? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Check for hex color
        if (hexRegex.matches(trimmed)) {
            val repr = IRColor.ColorRepresentation.Hex(trimmed)
            val srgb = ColorConversion.hexToSrgb(trimmed)
            return IRColor(repr, srgb)
        }

        // Check for relative color syntax: rgb(from red ...), hsl(from blue ...), oklch(from green ...), etc.
        // Dynamic - cannot compute sRGB at parse time
        if (lower.matches(Regex("^(rgb|rgba|hsl|hsla|hwb|lab|lch|oklab|oklch)\\s*\\(\\s*from\\s+.+"))) {
            return parseRelativeColor(trimmed)
        }

        // Check for RGB/RGBA (comma-separated)
        rgbCommaRegex.find(trimmed)?.let { match ->
            return parseRgb(match)
        }

        // Check for RGB/RGBA (space-separated)
        rgbSpaceRegex.find(trimmed)?.let { match ->
            return parseRgb(match)
        }

        // Check for HSL/HSLA (comma-separated)
        hslCommaRegex.find(trimmed)?.let { match ->
            return parseHsl(match)
        }

        // Check for HSL/HSLA (space-separated)
        hslSpaceRegex.find(trimmed)?.let { match ->
            return parseHsl(match)
        }

        // Check for HWB
        hwbRegex.find(trimmed)?.let { match ->
            // Hue through the shared <hue> normaliser (units → degrees,
            // none → 0); whiteness/blackness are percentages, so the
            // regex keeps the % sign and parseLabValue strips it
            // (none → 0 there too, css-color-4 §4.1).
            val h = parseHslHue(match.groupValues[1])
            val w = parseLabValue(match.groupValues[2]) ?: return null
            val b = parseLabValue(match.groupValues[3]) ?: return null
            val alpha = parseAlpha(match.groupValues.getOrNull(4))
            val repr = IRColor.ColorRepresentation.HWB(h = h, w = w, b = b, alpha = alpha)
            val srgb = ColorConversion.hwbToSrgb(h, w, b, alpha)
            return IRColor(repr, srgb)
        }

        // Check for Lab
        labRegex.find(trimmed)?.let { match ->
            val lStr = match.groupValues[1]
            val aStr = match.groupValues[2]
            val bStr = match.groupValues[3]
            val l = parseLabValue(lStr) ?: return null
            // a/b percentages scale ±100% → ±125 (css-color-4 §9.1); bare
            // numbers pass through (scale only applies to the % arm).
            val a = parseScaledComponent(aStr, 1.25) ?: return null
            val b = parseScaledComponent(bStr, 1.25) ?: return null
            val alpha = parseAlpha(match.groupValues.getOrNull(4))
            val repr = IRColor.ColorRepresentation.Lab(l = l, a = a, b = b, alpha = alpha)
            val srgb = ColorConversion.labToSrgb(l, a, b, alpha).clamped()
            return IRColor(repr, srgb)
        }

        // Check for LCH
        lchRegex.find(trimmed)?.let { match ->
            val lStr = match.groupValues[1]
            val cStr = match.groupValues[2]
            val hStr = match.groupValues[3]
            val l = parseLabValue(lStr) ?: return null
            // Chroma % scales 100% → 150 (css-color-4 §8.2) and negatives
            // clamp to 0 at parsed-value time — the repr stores the CANONICAL
            // number so web re-emission (`lch(50 150 0)`) is valid CSS.
            val c = (parseScaledComponent(cStr, 1.5) ?: return null).coerceAtLeast(0.0)
            // Hue is an <angle>: normalise units to degrees like hsl does.
            val h = parseHslHue(hStr)
            val alpha = parseAlpha(match.groupValues.getOrNull(4))
            val repr = IRColor.ColorRepresentation.LCH(l = l, c = c, h = h, alpha = alpha)
            val srgb = ColorConversion.lchToSrgb(l, c, h, alpha).clamped()
            return IRColor(repr, srgb)
        }

        // Check for OKLab
        oklabRegex.find(trimmed)?.let { match ->
            val lStr = match.groupValues[1]
            val aStr = match.groupValues[2]
            val bStr = match.groupValues[3]
            // ok-space lightness reference is 0..1 (css-color-4 §9.2:
            // 100% = 1.0), so the % arm scales by 0.01 — the repr then holds
            // the CANONICAL number (`oklab(86.64% …)` → l=0.8664) instead of
            // relying on the conversion-side `>1 → /100` heuristic, which a
            // typed-original re-emission would otherwise misread as L>1.
            val l = parseScaledComponent(lStr, 0.01) ?: return null
            // a/b percentages scale ±100% → ±0.4 (css-color-4 §9.2).
            val a = parseScaledComponent(aStr, 0.004) ?: return null
            val b = parseScaledComponent(bStr, 0.004) ?: return null
            val alpha = parseAlpha(match.groupValues.getOrNull(4))
            val repr = IRColor.ColorRepresentation.OKLab(l = l, a = a, b = b, alpha = alpha)
            val srgb = ColorConversion.oklabToSrgb(l, a, b, alpha).clamped()
            return IRColor(repr, srgb)
        }

        // Check for OKLCH
        oklchRegex.find(trimmed)?.let { match ->
            val lStr = match.groupValues[1]
            val cStr = match.groupValues[2]
            val hStr = match.groupValues[3]
            // ok-space lightness: 100% = 1.0 — same canonical-number rule
            // as the oklab branch above (css-color-4 §9.2).
            val l = parseScaledComponent(lStr, 0.01) ?: return null
            // Chroma % scales 100% → 0.4 (css-color-4 §8.2), negative-clamped
            // like the lch branch above.
            val c = (parseScaledComponent(cStr, 0.004) ?: return null).coerceAtLeast(0.0)
            // Hue is an <angle> — same normalisation as lch above.
            val h = parseHslHue(hStr)
            val alpha = parseAlpha(match.groupValues.getOrNull(4))
            val repr = IRColor.ColorRepresentation.OKLCH(l = l, c = c, h = h, alpha = alpha)
            val srgb = ColorConversion.oklchToSrgb(l, c, h, alpha).clamped()
            return IRColor(repr, srgb)
        }

        // Check for color-mix (including nested) - Dynamic
        if (trimmed.lowercase().startsWith("color-mix(")) {
            return parseColorMix(trimmed)
        }

        // Check for light-dark() function (CSS Color Level 5) - Dynamic
        if (trimmed.lowercase().startsWith("light-dark(")) {
            return parseLightDark(trimmed)
        }

        // Check for color() function (display-p3, srgb, etc.)
        colorFunctionRegex.find(trimmed)?.let { match ->
            val colorSpace = match.groupValues[1].lowercase()
            val v1 = match.groupValues[2].toDoubleOrNull() ?: return null
            val v2 = match.groupValues[3].toDoubleOrNull() ?: return null
            val v3 = match.groupValues[4].toDoubleOrNull() ?: return null
            val alpha = parseAlpha(match.groupValues.getOrNull(5))
            val repr = IRColor.ColorRepresentation.ColorFunction(colorSpace, listOf(v1, v2, v3), alpha)
            // Compute the normalized sRGB per predefined color space (CSS Color 4 §16).
            // Each space is primaries→XYZ(D65)→sRGB; results are gamut-clipped to [0,1]
            // (simple clip — a true gamut map is a later refinement). The `original`
            // still carries the untouched colorSpace + values, so this only ADDS the
            // `srgb` field the runtimes already read (no wire-shape change).
            val srgb = when (colorSpace) {
                // Plain sRGB: values are already sRGB channels; just clip out-of-gamut.
                "srgb" -> SRGB(v1, v2, v3, alpha).clamped()
                // Linear-light sRGB — gamma-encode each channel.
                "srgb-linear" -> ColorConversion.srgbLinearToSrgb(v1, v2, v3, alpha).clamped()
                // Wide-gamut RGB spaces — decode transfer function, primaries→XYZ→sRGB.
                "display-p3" -> ColorConversion.displayP3ToSrgb(v1, v2, v3, alpha).clamped()
                "a98-rgb" -> ColorConversion.a98RgbToSrgb(v1, v2, v3, alpha).clamped()
                "rec2020" -> ColorConversion.rec2020ToSrgb(v1, v2, v3, alpha).clamped()
                // CIE XYZ inputs — xyz defaults to D65; xyz-d50 needs Bradford adaptation.
                "xyz", "xyz-d65" -> ColorConversion.xyzD65ToSrgb(v1, v2, v3, alpha).clamped()
                "xyz-d50" -> ColorConversion.xyzD50ToSrgb(v1, v2, v3, alpha).clamped()
                else -> null // Unknown color space - leave srgb null (runtime-dependent)
            }
            return IRColor(repr, srgb)
        }
        // Fallback for complex color() expressions - Dynamic
        if (trimmed.lowercase().startsWith("color(")) {
            return IRColor(IRColor.ColorRepresentation.Named(trimmed), null)
        }

        // Check for CSS variables - Dynamic
        if (trimmed.lowercase().startsWith("var(")) {
            return IRColor(IRColor.ColorRepresentation.Named(trimmed), null)
        }

        // Check for env() function - Dynamic
        if (trimmed.lowercase().startsWith("env(")) {
            return IRColor(IRColor.ColorRepresentation.Named(trimmed), null)
        }

        // Check for special keywords
        when (lower) {
            "transparent" -> return IRColor(
                IRColor.ColorRepresentation.Transparent(),
                SRGB(0.0, 0.0, 0.0, 0.0)
            )
            "currentcolor" -> return IRColor(
                IRColor.ColorRepresentation.CurrentColor(),
                null // Dynamic - depends on context
            )
            // CSS global keywords - Dynamic
            "inherit", "initial", "unset", "revert", "revert-layer" ->
                return IRColor(IRColor.ColorRepresentation.Named(trimmed), null)
        }

        // Assume it's a named color (includes hyphenated names like alice-blue)
        if (trimmed.matches("""^[a-zA-Z][a-zA-Z0-9-]*$""".toRegex())) {
            val repr = IRColor.ColorRepresentation.Named(trimmed)
            val srgb = ColorConversion.namedColorToSrgb(trimmed)
            return IRColor(repr, srgb)
        }

        return null
    }

    private fun parseRgb(match: MatchResult): IRColor {
        val rStr = match.groupValues[1]
        val gStr = match.groupValues[2]
        val bStr = match.groupValues[3]

        // Handle 'none' keyword and percentage values
        val r = parseRgbValue(rStr)
        val g = parseRgbValue(gStr)
        val b = parseRgbValue(bStr)
        val a = parseAlpha(match.groupValues.getOrNull(4))

        val repr = IRColor.ColorRepresentation.RGB(r = r, g = g, b = b, a = a)
        val srgb = ColorConversion.rgb255ToSrgb(r, g, b, a)
        return IRColor(repr, srgb)
    }

    /**
     * Parse RGB component value - handles 'none', percentages, and raw values.
     */
    private fun parseRgbValue(value: String): Int {
        val trimmed = value.trim().lowercase()
        if (trimmed == "none") return 0
        return if (value.endsWith("%")) {
            (value.dropLast(1).toDouble() * 2.55).toInt()
        } else {
            value.toDouble().toInt()
        }
    }

    private fun parseHsl(match: MatchResult): IRColor {
        val hStr = match.groupValues[1]
        val sStr = match.groupValues[2]
        val lStr = match.groupValues[3]

        // Hue is an <angle>-or-<number>: normalize any unit to degrees (parseHslHue).
        // Saturation/lightness are plain <percentage> numbers (parseHslValue).
        val h = parseHslHue(hStr)
        val s = parseHslValue(sStr)
        val l = parseHslValue(lStr)
        val a = parseAlpha(match.groupValues.getOrNull(4))

        val repr = IRColor.ColorRepresentation.HSL(h = h, s = s, l = l, a = a)
        val srgb = ColorConversion.hslToSrgb(h, s, l, a)
        return IRColor(repr, srgb)
    }

    /**
     * Parse HSL component value - handles 'none' keyword.
     */
    private fun parseHslValue(value: String): Double {
        val trimmed = value.trim().lowercase()
        if (trimmed == "none") return 0.0
        return trimmed.toDoubleOrNull() ?: 0.0
    }

    /**
     * Parse the HSL hue component into degrees.
     *
     * Per CSS Color 4 §7, the hue is an <angle> or a <number>:
     * - A bare number (or scientific notation like 1.2e2) is degrees.
     * - deg/grad/rad/turn are <angle> units; AngleParser reuses IRAngle's shared
     *   conversion constants (grad ×0.9, rad ×180/π, turn ×360) — no duplication here.
     * The returned degrees are handed to ColorConversion.hslToSrgb, which already
     * wraps mod 360 (so e.g. hsl(600deg) → 240 = blue) — the hue→rgb math is unchanged.
     */
    private fun parseHslHue(value: String): Double {
        val trimmed = value.trim().lowercase()
        if (trimmed == "none") return 0.0 // 'none' contributes hue 0
        // Angle with a unit (deg/grad/rad/turn) → normalize to degrees.
        AngleParser.parse(trimmed)?.let { return it.degrees }
        // Bare number or scientific notation (no unit): already degrees.
        // Kotlin's toDouble handles exponents (e.g. "1.2e2" == 120.0).
        return trimmed.toDoubleOrNull() ?: 0.0
    }

    private fun parseAlpha(alphaStr: String?): Double {
        if (alphaStr.isNullOrBlank()) return 1.0
        return if (alphaStr.endsWith("%")) {
            alphaStr.dropLast(1).toDoubleOrNull()?.div(100) ?: 1.0
        } else {
            alphaStr.toDoubleOrNull() ?: 1.0
        }
    }

    /**
     * Parse lab/lch/oklab/oklch component value.
     * Handles: numeric values, percentages, and 'none' keyword.
     *
     * NOTE the % arm returns the bare number (`50%` → 50.0) — correct for
     * lab/lch LIGHTNESS only, whose reference range is 0..100 so 100% = 100
     * (css-color-4 §9.1). Every channel whose percentage reference differs
     * (chroma, a/b, the ok-space lightness) goes through
     * [parseScaledComponent] instead.
     */
    private fun parseLabValue(value: String): Double? {
        val trimmed = value.trim().lowercase()
        if (trimmed == "none") return 0.0 // 'none' is treated as 0
        return if (trimmed.endsWith("%")) {
            trimmed.dropLast(1).toDoubleOrNull()
        } else {
            trimmed.toDoubleOrNull()
        }
    }

    /**
     * Parse a lab/lch/oklab/oklch component whose PERCENTAGE arm has a
     * non-unit reference value (wave-48 lane W5, css-color-4 §8.2/§9.1/§9.2):
     *
     *   channel     | 100% equals | percentScale (per 1%)
     *   ------------|-------------|----------------------
     *   lab a/b     | ±125        | 1.25
     *   lch chroma  | 150         | 1.5
     *   oklab a/b   | ±0.4        | 0.004
     *   oklch chroma| 0.4         | 0.004
     *
     * A bare <number> passes through unscaled (the scale belongs to the %
     * arm only), and `none` stays 0 exactly like [parseLabValue] — callers
     * that need the chroma negative-clamp apply it themselves so the a/b
     * axes (legitimately negative) share this helper.
     */
    private fun parseScaledComponent(value: String, percentScale: Double): Double? {
        val trimmed = value.trim().lowercase()
        if (trimmed == "none") return 0.0 // css-color-4 §4.1: missing behaves as 0
        return if (trimmed.endsWith("%")) {
            trimmed.dropLast(1).toDoubleOrNull()?.times(percentScale)
        } else {
            trimmed.toDoubleOrNull()
        }
    }

    /**
     * Parse color-mix() function.
     * Syntax: color-mix(in <color-space> [<hue-method>], <color>[<percentage>], <color>[<percentage>])
     * Note: sRGB is null because color-mix requires runtime evaluation.
     */
    private fun parseColorMix(value: String): IRColor {
        val inner = value.trim().removePrefix("color-mix(").removeSuffix(")").trim()

        // Split by comma, respecting nested parentheses
        val parts = splitByComma(inner)
        if (parts.size < 2) return IRColor(IRColor.ColorRepresentation.Named(value), null)

        // Parse "in <color-space> [<hue-method>]"
        val inPart = parts[0].trim()
        if (!inPart.lowercase().startsWith("in ")) {
            return IRColor(IRColor.ColorRepresentation.Named(value), null)
        }

        val spaceAndMethod = inPart.removePrefix("in ").removePrefix("IN ").trim().split("\\s+".toRegex())
        val colorSpace = spaceAndMethod[0]
        val hueMethod = if (spaceAndMethod.size > 1) {
            spaceAndMethod.subList(1, spaceAndMethod.size).joinToString(" ")
        } else null

        // Parse colors with optional percentages
        val colorParts = if (parts.size == 2) {
            // Single comma means two colors separated by comma
            val remaining = parts[1].trim()
            remaining.split(",").map { it.trim() }
        } else {
            parts.subList(1, parts.size).map { it.trim() }
        }

        if (colorParts.size < 2) return IRColor(IRColor.ColorRepresentation.Named(value), null)

        val (color1, percent1) = parseColorWithPercent(colorParts[0])
        val (color2, percent2) = parseColorWithPercent(colorParts[1])

        // sRGB is null - color-mix is dynamic and requires runtime evaluation
        return IRColor(IRColor.ColorRepresentation.ColorMix(
            colorSpace = colorSpace,
            hueMethod = hueMethod,
            color1 = color1,
            percent1 = percent1,
            color2 = color2,
            percent2 = percent2
        ), null)
    }

    /**
     * Parse light-dark() function.
     * Syntax: light-dark(<light-color>, <dark-color>)
     * Note: sRGB is null because light-dark depends on user's color scheme preference.
     */
    private fun parseLightDark(value: String): IRColor {
        val inner = value.trim().removePrefix("light-dark(").removeSuffix(")").trim()
        val parts = splitByComma(inner)

        if (parts.size != 2) return IRColor(IRColor.ColorRepresentation.Named(value), null)

        // sRGB is null - light-dark is dynamic based on user's color scheme
        return IRColor(IRColor.ColorRepresentation.LightDark(
            lightColor = parts[0].trim(),
            darkColor = parts[1].trim()
        ), null)
    }

    /**
     * Parse relative color syntax.
     * Syntax: <color-function>(from <base-color> <component1> <component2> <component3>)
     * Note: sRGB is null because relative colors depend on the base color and may include calc().
     */
    private fun parseRelativeColor(value: String): IRColor {
        val match = Regex("^(rgb|rgba|hsl|hsla|hwb|lab|lch|oklab|oklch)\\s*\\(\\s*from\\s+(.+)\\)$", RegexOption.IGNORE_CASE)
            .find(value.trim())

        if (match == null) return IRColor(IRColor.ColorRepresentation.Named(value), null)

        val function = match.groupValues[1].lowercase()
        val inner = match.groupValues[2].trim()

        // Split components - first token is the base color, rest are component expressions
        val tokens = splitTokens(inner)
        if (tokens.isEmpty()) return IRColor(IRColor.ColorRepresentation.Named(value), null)

        val baseColor = tokens[0]
        val components = tokens.drop(1)

        // sRGB is null - relative colors are dynamic and may contain calc() expressions
        return IRColor(IRColor.ColorRepresentation.RelativeColor(
            function = function,
            baseColor = baseColor,
            components = components
        ), null)
    }

    /**
     * Split string by comma, respecting nested parentheses.
     */
    private fun splitByComma(value: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()

        for (char in value) {
            when (char) {
                '(' -> { depth++; current.append(char) }
                ')' -> { depth--; current.append(char) }
                ',' -> {
                    if (depth == 0) {
                        result.add(current.toString())
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }

    /**
     * Parse color with optional percentage (e.g., "red 50%", "blue", "#ff0000 25%")
     */
    private fun parseColorWithPercent(value: String): Pair<String, Double?> {
        val trimmed = value.trim()
        val percentMatch = Regex("(.+)\\s+(\\d+(?:\\.\\d+)?)%$").find(trimmed)

        return if (percentMatch != null) {
            Pair(percentMatch.groupValues[1].trim(), percentMatch.groupValues[2].toDoubleOrNull())
        } else {
            Pair(trimmed, null)
        }
    }

    /**
     * Split into tokens, respecting parentheses and calc() expressions.
     */
    private fun splitTokens(value: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()

        for (char in value) {
            when {
                char == '(' -> { depth++; current.append(char) }
                char == ')' -> { depth--; current.append(char) }
                char.isWhitespace() && depth == 0 -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
