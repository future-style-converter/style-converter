package app.parsing.css.properties.longhands.effects

import app.irmodels.IRPercentage
import app.irmodels.properties.color.FilterFunction
import app.irmodels.properties.effects.BackdropFilterProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.*
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for the `backdrop-filter` CSS property.
 * Uses shared FilterFunction from color package.
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
 * - opacity(50%)
 * - none (empty list)
 */
object BackdropFilterPropertyParser : PropertyParser {

    private val functionRegex = """(\w+[-\w]*)\(([^)]+)\)""".toRegex()
    override fun parse(value: String): BackdropFilterProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle "none" keyword - return empty filter list
        if (lower == "none") {
            return BackdropFilterProperty.fromFilters(emptyList())
        }

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return BackdropFilterProperty.fromKeyword(lower)
        }

        // Check for var() or other complex expressions that we can't fully parse
        if (ExpressionDetector.containsExpression(lower)) {
            return BackdropFilterProperty.fromRaw(trimmed)
        }

        // Parse filter functions
        val functions = mutableListOf<FilterFunction>()
        val matches = functionRegex.findAll(value)

        for (match in matches) {
            val functionName = match.groupValues[1].lowercase()
            val args = match.groupValues[2].trim()

            val filterFunction = when (functionName) {
                "blur" -> {
                    val length = LengthParser.parse(args)
                    if (length == null) return BackdropFilterProperty.fromRaw(trimmed)
                    FilterFunction.Blur(length)
                }
                "brightness" -> {
                    val amount = parsePercentageOrNumber(args)
                    if (amount == null) return BackdropFilterProperty.fromRaw(trimmed)
                    FilterFunction.Brightness(amount)
                }
                "contrast" -> {
                    val amount = parsePercentageOrNumber(args)
                    if (amount == null) return BackdropFilterProperty.fromRaw(trimmed)
                    FilterFunction.Contrast(amount)
                }
                "grayscale" -> {
                    val amount = parsePercentageOrNumber(args)
                    if (amount == null) return BackdropFilterProperty.fromRaw(trimmed)
                    FilterFunction.Grayscale(amount)
                }
                "hue-rotate" -> {
                    val angle = AngleParser.parse(args)
                    if (angle == null) return BackdropFilterProperty.fromRaw(trimmed)
                    FilterFunction.HueRotate(angle)
                }
                "invert" -> {
                    val amount = parsePercentageOrNumber(args)
                    if (amount == null) return BackdropFilterProperty.fromRaw(trimmed)
                    FilterFunction.Invert(amount)
                }
                "saturate" -> {
                    val amount = parsePercentageOrNumber(args)
                    if (amount == null) return BackdropFilterProperty.fromRaw(trimmed)
                    FilterFunction.Saturate(amount)
                }
                "sepia" -> {
                    val amount = parsePercentageOrNumber(args)
                    if (amount == null) return BackdropFilterProperty.fromRaw(trimmed)
                    FilterFunction.Sepia(amount)
                }
                "opacity" -> {
                    val amount = parsePercentageOrNumber(args)
                    if (amount == null) return BackdropFilterProperty.fromRaw(trimmed)
                    FilterFunction.Opacity(amount)
                }
                "drop-shadow" -> {
                    parseDropShadow(args) ?: return BackdropFilterProperty.fromRaw(trimmed)
                }
                else -> return BackdropFilterProperty.fromRaw(trimmed) // Unknown filter function
            }

            functions.add(filterFunction)
        }

        if (functions.isEmpty()) {
            return BackdropFilterProperty.fromRaw(trimmed)
        }

        return BackdropFilterProperty.fromFilters(functions)
    }

    /**
     * Parse percentage or number value.
     * Returns IRPercentage normalized (e.g., 150% → 150.0, 1.5 → 150.0)
     */
    private fun parsePercentageOrNumber(value: String): IRPercentage? {
        val trimmed = value.trim()

        // Try percentage first
        PercentageParser.parse(trimmed)?.let { return it }

        // Try number (convert to percentage, e.g., 1.5 → 150%)
        NumberParser.parseDouble(trimmed)?.let {
            return IRPercentage(it * 100.0)
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
