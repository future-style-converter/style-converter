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

    // Legacy comma-separated HSL: hsl(120, 100%, 50%) - supports 'none' keyword
    private val hslCommaRegex = """^hsla?\s*\(\s*([\d.]+|none)(?:deg)?\s*,\s*([\d.]+|none)%?\s*,\s*([\d.]+|none)%?\s*(?:,\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // Modern space-separated HSL: hsl(120deg 100% 50%) or hsl(120 100% 50% / 50%) - supports 'none' keyword
    private val hslSpaceRegex = """^hsla?\s*\(\s*([\d.]+|none)(?:deg)?\s+([\d.]+|none)%?\s+([\d.]+|none)%?\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // HWB: hwb(120 0% 0%) or hwb(120deg 0% 0% / 50%)
    private val hwbRegex = """^hwb\s*\(\s*([\d.]+)(?:deg)?\s+([\d.]+)%\s+([\d.]+)%\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex()

    // Lab: lab(50% 25 -25) or lab(50% 25 -25 / 50%) - supports 'none' keyword
    private val labRegex = """^lab\s*\(\s*([\d.]+%?|none)\s+(-?[\d.]+|none)\s+(-?[\d.]+|none)\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // LCH: lch(50% 25 180) or lch(50% 25 180deg / 50%) - supports 'none' keyword
    private val lchRegex = """^lch\s*\(\s*([\d.]+%?|none)\s+([\d.]+|none)\s+([\d.]+|none)(?:deg)?\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // OKLab: oklab(0.7 -0.1 0.15) or oklab(70% -0.1 0.15 / 50%) - supports 'none' keyword
    private val oklabRegex = """^oklab\s*\(\s*([\d.]+%?|none)\s+(-?[\d.]+|none)\s+(-?[\d.]+|none)\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

    // OKLCH: oklch(0.7 0.15 180) or oklch(70% 0.15 180deg / 50%) - supports 'none' keyword
    private val oklchRegex = """^oklch\s*\(\s*([\d.]+%?|none)\s+([\d.]+|none)\s+([\d.]+|none)(?:deg)?\s*(?:/\s*([\d.]+%?))?\s*\)$""".toRegex(RegexOption.IGNORE_CASE)

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
            val h = match.groupValues[1].toDoubleOrNull() ?: return null
            val w = match.groupValues[2].toDoubleOrNull() ?: return null
            val b = match.groupValues[3].toDoubleOrNull() ?: return null
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
            val a = parseLabValue(aStr) ?: return null
            val b = parseLabValue(bStr) ?: return null
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
            val c = parseLabValue(cStr) ?: return null
            val h = parseLabValue(hStr) ?: return null
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
            val l = parseLabValue(lStr) ?: return null
            val a = parseLabValue(aStr) ?: return null
            val b = parseLabValue(bStr) ?: return null
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
            val l = parseLabValue(lStr) ?: return null
            val c = parseLabValue(cStr) ?: return null
            val h = parseLabValue(hStr) ?: return null
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
            // Compute sRGB based on color space
            val srgb = when (colorSpace) {
                "srgb" -> SRGB(v1, v2, v3, alpha)
                "display-p3" -> ColorConversion.displayP3ToSrgb(v1, v2, v3, alpha).clamped()
                else -> null // Unknown color space - can't convert
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

        // Handle 'none' keyword
        val h = parseHslValue(hStr)
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
