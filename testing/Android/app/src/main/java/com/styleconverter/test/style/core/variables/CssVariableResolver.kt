package com.styleconverter.test.style.core.variables

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Resolves CSS variable expressions at runtime.
 *
 * Handles `var(--name)` and `var(--name, fallback)` expressions
 * by looking up values in the current [CssVariableScope].
 *
 * ## Resolution Strategy
 * 1. Check if value contains `var()` expression
 * 2. Parse the variable name and optional fallback
 * 3. Look up in current scope (from CompositionLocal)
 * 4. Use fallback if variable not found
 * 5. Return null if neither variable nor fallback available
 *
 * ## Nested Variables
 * Supports nested var() expressions in fallbacks:
 * `var(--color, var(--fallback-color, #000))`
 */
object CssVariableResolver {

    private val VAR_PATTERN = Regex("""var\(\s*(--[a-zA-Z0-9_-]+)\s*(?:,\s*(.+))?\s*\)""")
    private val LENGTH_PATTERN = Regex("""^(-?\d+(?:\.\d+)?)\s*(px|dp|em|rem|%)?$""")
    private val COLOR_HEX_PATTERN = Regex("""^#([0-9a-fA-F]{3,8})$""")

    /**
     * Resolve all var() expressions in a value string.
     *
     * @param value The CSS value that may contain var() expressions
     * @param scope The current variable scope for lookups
     * @param maxDepth Maximum recursion depth for nested variables
     * @return The resolved value, or null if resolution fails
     */
    fun resolve(value: String, scope: CssVariableScope, maxDepth: Int = 10): String? {
        if (maxDepth <= 0) return null
        if (!value.contains("var(")) return value

        var result = value
        var match = VAR_PATTERN.find(result)

        while (match != null) {
            val variableName = match.groupValues[1]
            val fallback = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }

            val resolved = scope.get(variableName)
                ?: fallback?.let { resolve(it, scope, maxDepth - 1) }
                ?: return null

            result = result.replaceRange(match.range, resolved)
            match = VAR_PATTERN.find(result)
        }

        return result
    }

    /**
     * Resolve a value and parse as a length (Dp).
     */
    @Composable
    fun resolveToDp(value: String): Dp? {
        val scope = LocalCssVariables.current
        val resolved = resolve(value, scope) ?: return null
        return parseDp(resolved)
    }

    /**
     * Resolve a value and parse as a color.
     */
    @Composable
    fun resolveToColor(value: String): Color? {
        val scope = LocalCssVariables.current
        val resolved = resolve(value, scope) ?: return null
        return parseColor(resolved)
    }

    /**
     * Resolve a value and parse as a text unit (sp).
     */
    @Composable
    fun resolveToTextUnit(value: String): TextUnit? {
        val scope = LocalCssVariables.current
        val resolved = resolve(value, scope) ?: return null
        return parseTextUnit(resolved)
    }

    /**
     * Resolve a value and parse as a float.
     */
    @Composable
    fun resolveToFloat(value: String): Float? {
        val scope = LocalCssVariables.current
        val resolved = resolve(value, scope) ?: return null
        return resolved.toFloatOrNull()
    }

    /**
     * Resolve a value to a raw string.
     */
    @Composable
    fun resolveToString(value: String): String? {
        val scope = LocalCssVariables.current
        return resolve(value, scope)
    }

    /**
     * Parse a length value to Dp.
     */
    private fun parseDp(value: String): Dp? {
        val match = LENGTH_PATTERN.find(value.trim()) ?: return null
        val number = match.groupValues[1].toFloatOrNull() ?: return null
        val unit = match.groupValues.getOrNull(2) ?: "px"

        return when (unit.lowercase()) {
            "px", "dp" -> number.dp
            "em", "rem" -> (number * 16).dp // Approximate to 16px base
            else -> number.dp
        }
    }

    /**
     * Parse a text unit value to Sp.
     */
    private fun parseTextUnit(value: String): TextUnit? {
        val match = LENGTH_PATTERN.find(value.trim()) ?: return null
        val number = match.groupValues[1].toFloatOrNull() ?: return null
        val unit = match.groupValues.getOrNull(2) ?: "px"

        return when (unit.lowercase()) {
            "px", "sp" -> number.sp
            "em", "rem" -> (number * 16).sp
            else -> number.sp
        }
    }

    private val RGB_PATTERN = Regex("""rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""", RegexOption.IGNORE_CASE)
    private val RGBA_PATTERN = Regex("""rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)""", RegexOption.IGNORE_CASE)
    private val HSL_PATTERN = Regex("""hsl\(\s*(\d+)\s*,\s*(\d+)%\s*,\s*(\d+)%\s*\)""", RegexOption.IGNORE_CASE)
    private val HSLA_PATTERN = Regex("""hsla\(\s*(\d+)\s*,\s*(\d+)%\s*,\s*(\d+)%\s*,\s*([\d.]+)\s*\)""", RegexOption.IGNORE_CASE)

    /**
     * Parse a color value.
     */
    private fun parseColor(value: String): Color? {
        val trimmed = value.trim()

        // Handle hex colors
        val hexMatch = COLOR_HEX_PATTERN.find(trimmed)
        if (hexMatch != null) {
            return parseHexColor(hexMatch.groupValues[1])
        }

        // Handle rgb()
        RGB_PATTERN.find(trimmed)?.let { match ->
            val r = match.groupValues[1].toIntOrNull() ?: return null
            val g = match.groupValues[2].toIntOrNull() ?: return null
            val b = match.groupValues[3].toIntOrNull() ?: return null
            return Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        }

        // Handle rgba()
        RGBA_PATTERN.find(trimmed)?.let { match ->
            val r = match.groupValues[1].toIntOrNull() ?: return null
            val g = match.groupValues[2].toIntOrNull() ?: return null
            val b = match.groupValues[3].toIntOrNull() ?: return null
            val a = match.groupValues[4].toFloatOrNull() ?: return null
            return Color(
                r.coerceIn(0, 255),
                g.coerceIn(0, 255),
                b.coerceIn(0, 255),
                (a.coerceIn(0f, 1f) * 255).toInt()
            )
        }

        // Handle hsl()
        HSL_PATTERN.find(trimmed)?.let { match ->
            val h = match.groupValues[1].toFloatOrNull() ?: return null
            val s = match.groupValues[2].toFloatOrNull()?.div(100f) ?: return null
            val l = match.groupValues[3].toFloatOrNull()?.div(100f) ?: return null
            return hslToColor(h, s, l, 1f)
        }

        // Handle hsla()
        HSLA_PATTERN.find(trimmed)?.let { match ->
            val h = match.groupValues[1].toFloatOrNull() ?: return null
            val s = match.groupValues[2].toFloatOrNull()?.div(100f) ?: return null
            val l = match.groupValues[3].toFloatOrNull()?.div(100f) ?: return null
            val a = match.groupValues[4].toFloatOrNull() ?: return null
            return hslToColor(h, s, l, a)
        }

        // Handle named colors
        return namedColors[trimmed.lowercase()]
    }

    /**
     * Convert HSL to Color.
     */
    private fun hslToColor(h: Float, s: Float, l: Float, alpha: Float): Color {
        val c = (1 - kotlin.math.abs(2 * l - 1)) * s
        val x = c * (1 - kotlin.math.abs((h / 60) % 2 - 1))
        val m = l - c / 2

        val (r1, g1, b1) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = alpha.coerceIn(0f, 1f)
        )
    }

    /**
     * Parse a hex color string.
     */
    private fun parseHexColor(hex: String): Color? {
        return when (hex.length) {
            3 -> {
                val r = hex[0].toString().repeat(2).toIntOrNull(16) ?: return null
                val g = hex[1].toString().repeat(2).toIntOrNull(16) ?: return null
                val b = hex[2].toString().repeat(2).toIntOrNull(16) ?: return null
                Color(r, g, b)
            }
            4 -> {
                val r = hex[0].toString().repeat(2).toIntOrNull(16) ?: return null
                val g = hex[1].toString().repeat(2).toIntOrNull(16) ?: return null
                val b = hex[2].toString().repeat(2).toIntOrNull(16) ?: return null
                val a = hex[3].toString().repeat(2).toIntOrNull(16) ?: return null
                Color(r, g, b, a)
            }
            6 -> {
                val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                Color(r, g, b)
            }
            8 -> {
                val r = hex.substring(0, 2).toIntOrNull(16) ?: return null
                val g = hex.substring(2, 4).toIntOrNull(16) ?: return null
                val b = hex.substring(4, 6).toIntOrNull(16) ?: return null
                val a = hex.substring(6, 8).toIntOrNull(16) ?: return null
                Color(r, g, b, a)
            }
            else -> null
        }
    }

    /**
     * All 147 CSS named colors.
     */
    private val namedColors = mapOf(
        "transparent" to Color.Transparent,
        "aliceblue" to Color(0xFFF0F8FF),
        "antiquewhite" to Color(0xFFFAEBD7),
        "aqua" to Color(0xFF00FFFF),
        "aquamarine" to Color(0xFF7FFFD4),
        "azure" to Color(0xFFF0FFFF),
        "beige" to Color(0xFFF5F5DC),
        "bisque" to Color(0xFFFFE4C4),
        "black" to Color.Black,
        "blanchedalmond" to Color(0xFFFFEBCD),
        "blue" to Color.Blue,
        "blueviolet" to Color(0xFF8A2BE2),
        "brown" to Color(0xFFA52A2A),
        "burlywood" to Color(0xFFDEB887),
        "cadetblue" to Color(0xFF5F9EA0),
        "chartreuse" to Color(0xFF7FFF00),
        "chocolate" to Color(0xFFD2691E),
        "coral" to Color(0xFFFF7F50),
        "cornflowerblue" to Color(0xFF6495ED),
        "cornsilk" to Color(0xFFFFF8DC),
        "crimson" to Color(0xFFDC143C),
        "cyan" to Color.Cyan,
        "darkblue" to Color(0xFF00008B),
        "darkcyan" to Color(0xFF008B8B),
        "darkgoldenrod" to Color(0xFFB8860B),
        "darkgray" to Color.DarkGray,
        "darkgrey" to Color.DarkGray,
        "darkgreen" to Color(0xFF006400),
        "darkkhaki" to Color(0xFFBDB76B),
        "darkmagenta" to Color(0xFF8B008B),
        "darkolivegreen" to Color(0xFF556B2F),
        "darkorange" to Color(0xFFFF8C00),
        "darkorchid" to Color(0xFF9932CC),
        "darkred" to Color(0xFF8B0000),
        "darksalmon" to Color(0xFFE9967A),
        "darkseagreen" to Color(0xFF8FBC8F),
        "darkslateblue" to Color(0xFF483D8B),
        "darkslategray" to Color(0xFF2F4F4F),
        "darkslategrey" to Color(0xFF2F4F4F),
        "darkturquoise" to Color(0xFF00CED1),
        "darkviolet" to Color(0xFF9400D3),
        "deeppink" to Color(0xFFFF1493),
        "deepskyblue" to Color(0xFF00BFFF),
        "dimgray" to Color(0xFF696969),
        "dimgrey" to Color(0xFF696969),
        "dodgerblue" to Color(0xFF1E90FF),
        "firebrick" to Color(0xFFB22222),
        "floralwhite" to Color(0xFFFFFAF0),
        "forestgreen" to Color(0xFF228B22),
        "fuchsia" to Color(0xFFFF00FF),
        "gainsboro" to Color(0xFFDCDCDC),
        "ghostwhite" to Color(0xFFF8F8FF),
        "gold" to Color(0xFFFFD700),
        "goldenrod" to Color(0xFFDAA520),
        "gray" to Color.Gray,
        "grey" to Color.Gray,
        "green" to Color(0xFF008000),
        "greenyellow" to Color(0xFFADFF2F),
        "honeydew" to Color(0xFFF0FFF0),
        "hotpink" to Color(0xFFFF69B4),
        "indianred" to Color(0xFFCD5C5C),
        "indigo" to Color(0xFF4B0082),
        "ivory" to Color(0xFFFFFFF0),
        "khaki" to Color(0xFFF0E68C),
        "lavender" to Color(0xFFE6E6FA),
        "lavenderblush" to Color(0xFFFFF0F5),
        "lawngreen" to Color(0xFF7CFC00),
        "lemonchiffon" to Color(0xFFFFFACD),
        "lightblue" to Color(0xFFADD8E6),
        "lightcoral" to Color(0xFFF08080),
        "lightcyan" to Color(0xFFE0FFFF),
        "lightgoldenrodyellow" to Color(0xFFFAFAD2),
        "lightgray" to Color.LightGray,
        "lightgrey" to Color.LightGray,
        "lightgreen" to Color(0xFF90EE90),
        "lightpink" to Color(0xFFFFB6C1),
        "lightsalmon" to Color(0xFFFFA07A),
        "lightseagreen" to Color(0xFF20B2AA),
        "lightskyblue" to Color(0xFF87CEFA),
        "lightslategray" to Color(0xFF778899),
        "lightslategrey" to Color(0xFF778899),
        "lightsteelblue" to Color(0xFFB0C4DE),
        "lightyellow" to Color(0xFFFFFFE0),
        "lime" to Color(0xFF00FF00),
        "limegreen" to Color(0xFF32CD32),
        "linen" to Color(0xFFFAF0E6),
        "magenta" to Color.Magenta,
        "maroon" to Color(0xFF800000),
        "mediumaquamarine" to Color(0xFF66CDAA),
        "mediumblue" to Color(0xFF0000CD),
        "mediumorchid" to Color(0xFFBA55D3),
        "mediumpurple" to Color(0xFF9370DB),
        "mediumseagreen" to Color(0xFF3CB371),
        "mediumslateblue" to Color(0xFF7B68EE),
        "mediumspringgreen" to Color(0xFF00FA9A),
        "mediumturquoise" to Color(0xFF48D1CC),
        "mediumvioletred" to Color(0xFFC71585),
        "midnightblue" to Color(0xFF191970),
        "mintcream" to Color(0xFFF5FFFA),
        "mistyrose" to Color(0xFFFFE4E1),
        "moccasin" to Color(0xFFFFE4B5),
        "navajowhite" to Color(0xFFFFDEAD),
        "navy" to Color(0xFF000080),
        "oldlace" to Color(0xFFFDF5E6),
        "olive" to Color(0xFF808000),
        "olivedrab" to Color(0xFF6B8E23),
        "orange" to Color(0xFFFFA500),
        "orangered" to Color(0xFFFF4500),
        "orchid" to Color(0xFFDA70D6),
        "palegoldenrod" to Color(0xFFEEE8AA),
        "palegreen" to Color(0xFF98FB98),
        "paleturquoise" to Color(0xFFAFEEEE),
        "palevioletred" to Color(0xFFDB7093),
        "papayawhip" to Color(0xFFFFEFD5),
        "peachpuff" to Color(0xFFFFDAB9),
        "peru" to Color(0xFFCD853F),
        "pink" to Color(0xFFFFC0CB),
        "plum" to Color(0xFFDDA0DD),
        "powderblue" to Color(0xFFB0E0E6),
        "purple" to Color(0xFF800080),
        "rebeccapurple" to Color(0xFF663399),
        "red" to Color.Red,
        "rosybrown" to Color(0xFFBC8F8F),
        "royalblue" to Color(0xFF4169E1),
        "saddlebrown" to Color(0xFF8B4513),
        "salmon" to Color(0xFFFA8072),
        "sandybrown" to Color(0xFFF4A460),
        "seagreen" to Color(0xFF2E8B57),
        "seashell" to Color(0xFFFFF5EE),
        "sienna" to Color(0xFFA0522D),
        "silver" to Color(0xFFC0C0C0),
        "skyblue" to Color(0xFF87CEEB),
        "slateblue" to Color(0xFF6A5ACD),
        "slategray" to Color(0xFF708090),
        "slategrey" to Color(0xFF708090),
        "snow" to Color(0xFFFFFAFA),
        "springgreen" to Color(0xFF00FF7F),
        "steelblue" to Color(0xFF4682B4),
        "tan" to Color(0xFFD2B48C),
        "teal" to Color(0xFF008080),
        "thistle" to Color(0xFFD8BFD8),
        "tomato" to Color(0xFFFF6347),
        "turquoise" to Color(0xFF40E0D0),
        "violet" to Color(0xFFEE82EE),
        "wheat" to Color(0xFFF5DEB3),
        "white" to Color.White,
        "whitesmoke" to Color(0xFFF5F5F5),
        "yellow" to Color.Yellow,
        "yellowgreen" to Color(0xFF9ACD32)
    )
}

/**
 * Extension function to check if a string contains unresolved var() expressions.
 */
fun String.hasUnresolvedVariables(): Boolean {
    return contains("var(")
}

/**
 * Extension function to resolve a string using the current variable scope.
 */
@Composable
fun String.resolveVariables(): String? {
    val scope = LocalCssVariables.current
    return CssVariableResolver.resolve(this, scope)
}
