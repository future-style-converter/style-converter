package app.parsing.css.properties.longhands.effects

import app.irmodels.properties.color.FilterProperty
import app.irmodels.properties.color.FilterFunction
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.*
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for the `filter` CSS property.
 *
 * Supports filter functions:
 * - blur(5px)
 * - brightness(1.5) or brightness(150%)
 * - contrast(200%)
 * - grayscale(100%)
 * - hue-rotate(90deg)
 * - invert(100%)
 * - saturate(200%)
 * - sepia(100%)
 * - drop-shadow(2px 2px 4px rgba(0,0,0,0.5))
 * - none
 */
object FilterPropertyParser : PropertyParser {

    override fun parse(value: String): FilterProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle "none" keyword
        if (lower == "none") {
            return FilterProperty(FilterProperty.FilterValue.None())
        }

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return FilterProperty(FilterProperty.FilterValue.Keyword(lower))
        }

        // Handle url() references (SVG filters)
        if (lower.startsWith("url(")) {
            val urlEnd = trimmed.indexOf(')')
            if (urlEnd != -1) {
                val url = trimmed.substring(4, urlEnd).trim().removeSurrounding("\"").removeSurrounding("'").removeSurrounding("#")
                return FilterProperty(FilterProperty.FilterValue.UrlReference(url))
            }
        }

        // Check for var() or other complex expressions that we can't fully parse
        if (ExpressionDetector.containsExpression(lower)) {
            return FilterProperty(FilterProperty.FilterValue.Raw(trimmed))
        }

        // Parse filter functions (handles nested parentheses)
        val functions = mutableListOf<FilterFunction>()
        val functionParts = splitFilterFunctions(value)

        for (part in functionParts) {
            val filterFunction = parseFilterFunction(part.trim())
            if (filterFunction == null) {
                // If we can't parse a function, fall back to Raw for the entire value
                return FilterProperty(FilterProperty.FilterValue.Raw(trimmed))
            }
            functions.add(filterFunction)
        }

        if (functions.isEmpty()) {
            // Fallback to Raw for unparseable values
            return FilterProperty(FilterProperty.FilterValue.Raw(trimmed))
        }

        return FilterProperty(FilterProperty.FilterValue.FilterList(functions))
    }

    private fun splitFilterFunctions(value: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var parenDepth = 0

        for (char in value) {
            when {
                char == '(' -> {
                    parenDepth++
                    current.append(char)
                }
                char == ')' -> {
                    parenDepth--
                    current.append(char)
                    if (parenDepth == 0 && current.isNotEmpty()) {
                        result.add(current.toString().trim())
                        current.clear()
                    }
                }
                char.isWhitespace() && parenDepth == 0 -> {
                    // Skip whitespace between functions
                    if (current.isNotEmpty() && !current.last().isWhitespace()) {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }

        return result.filter { it.isNotBlank() }
    }

    private fun parseFilterFunction(value: String): FilterFunction? {
        val openParen = value.indexOf('(')
        if (openParen == -1 || !value.endsWith(')')) return null

        val functionName = value.substring(0, openParen).trim().lowercase()
        val args = value.substring(openParen + 1, value.length - 1).trim()

        return when (functionName) {
            "blur" -> {
                val length = LengthParser.parse(args) ?: return null
                FilterFunction.Blur(length)
            }
            "brightness" -> {
                val amount = parsePercentageOrNumber(args) ?: return null
                FilterFunction.Brightness(amount)
            }
            "contrast" -> {
                val amount = parsePercentageOrNumber(args) ?: return null
                FilterFunction.Contrast(amount)
            }
            "grayscale" -> {
                val amount = parsePercentageOrNumber(args) ?: return null
                FilterFunction.Grayscale(amount)
            }
            "hue-rotate" -> {
                val angle = AngleParser.parse(args) ?: return null
                FilterFunction.HueRotate(angle)
            }
            "invert" -> {
                val amount = parsePercentageOrNumber(args) ?: return null
                FilterFunction.Invert(amount)
            }
            "opacity" -> {
                val amount = parsePercentageOrNumber(args) ?: return null
                FilterFunction.Opacity(amount)
            }
            "saturate" -> {
                val amount = parsePercentageOrNumber(args) ?: return null
                FilterFunction.Saturate(amount)
            }
            "sepia" -> {
                val amount = parsePercentageOrNumber(args) ?: return null
                FilterFunction.Sepia(amount)
            }
            "drop-shadow" -> parseDropShadow(args)
            else -> null // Unknown filter function
        }
    }

    /**
     * Parse percentage or number value.
     * Returns IRPercentage normalized (e.g., 150% → 150.0, 1.5 → 150.0)
     */
    private fun parsePercentageOrNumber(value: String): app.irmodels.IRPercentage? {
        val trimmed = value.trim()

        // Try percentage first
        PercentageParser.parse(trimmed)?.let { return it }

        // Try number (convert to percentage, e.g., 1.5 → 150%)
        NumberParser.parseDouble(trimmed)?.let {
            return app.irmodels.IRPercentage(it * 100.0)
        }

        return null
    }

    /**
     * Parse drop-shadow function arguments.
     * Format: <offset-x> <offset-y> [<blur-radius>] [<color>]
     */
    private fun parseDropShadow(args: String): FilterFunction.DropShadow? {
        val parts = args.split("""\s+""".toRegex())

        if (parts.size < 2) return null

        val offsetX = LengthParser.parse(parts[0]) ?: return null
        val offsetY = LengthParser.parse(parts[1]) ?: return null

        var blurRadius: app.irmodels.IRLength? = null
        var color: app.irmodels.IRColor? = null

        // Parse optional blur and color
        if (parts.size > 2) {
            // Try to parse as length (blur radius)
            LengthParser.parse(parts[2])?.let {
                blurRadius = it
                // If there's a 4th part, try to parse as color
                if (parts.size > 3) {
                    color = ColorParser.parse(parts.subList(3, parts.size).joinToString(" "))
                }
            } ?: run {
                // Not a length, try to parse as color
                color = ColorParser.parse(parts.subList(2, parts.size).joinToString(" "))
            }
        }

        return FilterFunction.DropShadow(offsetX, offsetY, blurRadius, color)
    }
}
