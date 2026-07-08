package app.parsing.css.properties.primitiveParsers

import app.irmodels.IRLength

/**
 * Parses CSS length values into IRLength instances with pixel normalization.
 *
 * Supports:
 * - Absolute units: px, pt, cm, mm, in, pc, Q → normalized to pixels
 * - Relative units: em, rem, ex, ch, lh, rlh, ic, cap → pixels = null
 * - Viewport units: vw, vh, vmin, vmax, vi, vb → pixels = null
 * - Small viewport: svw, svh, svb, svi, svmin, svmax → pixels = null
 * - Large viewport: lvw, lvh, lvb, lvi, lvmin, lvmax → pixels = null
 * - Dynamic viewport: dvw, dvh, dvb, dvi, dvmin, dvmax → pixels = null
 * - Container query: cqw, cqh, cqi, cqb, cqmin, cqmax → pixels = null
 * - Percentage: % → pixels = null
 * - Special: fr (grid fractional unit) → pixels = null
 *
 * Examples:
 * - "10px" → IRLength(px=10.0, original=10.0, unit=PX)
 * - "12pt" → IRLength(px=16.0, original=12.0, unit=PT)
 * - "2em" → IRLength(px=null, original=2.0, unit=EM)
 * - "50%" → IRLength(px=null, original=50.0, unit=PERCENT)
 */
object LengthParser {

    // All supported CSS length units - order matters (longer units first to avoid partial matches)
    private val allUnits = listOf(
        // Multi-char units first (to avoid partial matching)
        "svmin", "svmax", "lvmin", "lvmax", "dvmin", "dvmax",
        "cqmin", "cqmax",
        "vmin", "vmax",
        "svw", "svh", "svb", "svi",
        "lvw", "lvh", "lvb", "lvi",
        "dvw", "dvh", "dvb", "dvi",
        "cqw", "cqh", "cqi", "cqb",
        "rem", "rlh", "cap",
        "em", "ex", "ch", "lh", "ic",
        "vw", "vh", "vi", "vb",
        "px", "dp", "sp", "pt", "cm", "mm", "in", "pc", "fr",
        "Q", "%"
    ).joinToString("|")

    private val lengthRegex = """^([+-]?\d*\.?\d+)($allUnits)$""".toRegex(RegexOption.IGNORE_CASE)

    /**
     * Parse a CSS length value, normalizing absolute units to pixels.
     *
     * @param value The length string (e.g., "10px", "50%", "1.5em", "0")
     * @return IRLength instance with normalized pixels (when applicable), or null if parsing fails
     */
    fun parse(value: String): IRLength? {
        val trimmed = value.trim()

        // Special case: CSS allows unitless zero
        if (trimmed == "0" || trimmed == "0.0") {
            return IRLength.fromPx(0.0)
        }

        // Match against length pattern
        val match = lengthRegex.find(trimmed) ?: return null

        val (numStr, unitStr) = match.destructured
        val numValue = numStr.toDoubleOrNull() ?: return null

        // Map unit string to LengthUnit and use factory method for normalization
        val unit = when (unitStr.lowercase()) {
            // Absolute units
            "px" -> IRLength.LengthUnit.PX
            "dp" -> IRLength.LengthUnit.DP
            "sp" -> IRLength.LengthUnit.SP
            "pt" -> IRLength.LengthUnit.PT
            "cm" -> IRLength.LengthUnit.CM
            "mm" -> IRLength.LengthUnit.MM
            "in" -> IRLength.LengthUnit.IN
            "pc" -> IRLength.LengthUnit.PC
            "q" -> IRLength.LengthUnit.Q

            // Font-relative units
            "em" -> IRLength.LengthUnit.EM
            "rem" -> IRLength.LengthUnit.REM
            "ex" -> IRLength.LengthUnit.EX
            "ch" -> IRLength.LengthUnit.CH
            "lh" -> IRLength.LengthUnit.LH
            "rlh" -> IRLength.LengthUnit.RLH
            "ic" -> IRLength.LengthUnit.IC
            "cap" -> IRLength.LengthUnit.CAP

            // Classic viewport units
            "vw" -> IRLength.LengthUnit.VW
            "vh" -> IRLength.LengthUnit.VH
            "vmin" -> IRLength.LengthUnit.VMIN
            "vmax" -> IRLength.LengthUnit.VMAX
            "vi" -> IRLength.LengthUnit.VI
            "vb" -> IRLength.LengthUnit.VB

            // Small viewport units
            "svw" -> IRLength.LengthUnit.SVW
            "svh" -> IRLength.LengthUnit.SVH
            "svb" -> IRLength.LengthUnit.SVB
            "svi" -> IRLength.LengthUnit.SVI
            "svmin" -> IRLength.LengthUnit.SVMIN
            "svmax" -> IRLength.LengthUnit.SVMAX

            // Large viewport units
            "lvw" -> IRLength.LengthUnit.LVW
            "lvh" -> IRLength.LengthUnit.LVH
            "lvb" -> IRLength.LengthUnit.LVB
            "lvi" -> IRLength.LengthUnit.LVI
            "lvmin" -> IRLength.LengthUnit.LVMIN
            "lvmax" -> IRLength.LengthUnit.LVMAX

            // Dynamic viewport units
            "dvw" -> IRLength.LengthUnit.DVW
            "dvh" -> IRLength.LengthUnit.DVH
            "dvb" -> IRLength.LengthUnit.DVB
            "dvi" -> IRLength.LengthUnit.DVI
            "dvmin" -> IRLength.LengthUnit.DVMIN
            "dvmax" -> IRLength.LengthUnit.DVMAX

            // Container query units
            "cqw" -> IRLength.LengthUnit.CQW
            "cqh" -> IRLength.LengthUnit.CQH
            "cqi" -> IRLength.LengthUnit.CQI
            "cqb" -> IRLength.LengthUnit.CQB
            "cqmin" -> IRLength.LengthUnit.CQMIN
            "cqmax" -> IRLength.LengthUnit.CQMAX

            // Other units
            "%" -> IRLength.LengthUnit.PERCENT
            "fr" -> IRLength.LengthUnit.FR

            else -> return null
        }

        // Use factory method which handles normalization automatically
        return IRLength.from(numValue, unit)
    }

    /**
     * Try to parse as length, returning a default if it fails.
     */
    fun parseOrDefault(value: String, default: IRLength): IRLength {
        return parse(value) ?: default
    }

    /**
     * Check if a value is a CSS expression (calc, clamp, min, max, var, env, or math functions).
     */
    fun isExpression(value: String): Boolean {
        val trimmed = value.trim().lowercase()
        return trimmed.startsWith("calc(") ||
               trimmed.startsWith("clamp(") ||
               trimmed.startsWith("min(") ||
               trimmed.startsWith("max(") ||
               trimmed.startsWith("var(") ||
               trimmed.startsWith("env(") ||
               // CSS math functions (Level 4)
               trimmed.startsWith("round(") ||
               trimmed.startsWith("mod(") ||
               trimmed.startsWith("rem(") ||
               trimmed.startsWith("abs(") ||
               trimmed.startsWith("sign(") ||
               trimmed.startsWith("pow(") ||
               trimmed.startsWith("sqrt(") ||
               trimmed.startsWith("hypot(") ||
               trimmed.startsWith("log(") ||
               trimmed.startsWith("exp(") ||
               trimmed.startsWith("sin(") ||
               trimmed.startsWith("cos(") ||
               trimmed.startsWith("tan(") ||
               trimmed.startsWith("asin(") ||
               trimmed.startsWith("acos(") ||
               trimmed.startsWith("atan(") ||
               trimmed.startsWith("atan2(")
    }
}
