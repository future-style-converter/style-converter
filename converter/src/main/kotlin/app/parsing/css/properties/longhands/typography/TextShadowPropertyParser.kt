package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextShadowProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ColorParser
import app.parsing.css.properties.primitiveParsers.LengthParser

/**
 * Parser for `text-shadow` property.
 *
 * Syntax: none | <shadow>#
 * where <shadow> = <offset-x> <offset-y> <blur-radius>? <color>?
 */
object TextShadowPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        // Handle "none" case
        if (trimmed.lowercase() == "none") {
            return TextShadowProperty(emptyList())
        }

        // Split by comma for multiple shadows
        val shadowStrings = trimmed.split(",").map { it.trim() }
        val shadows = shadowStrings.mapNotNull { parseShadow(it) }

        if (shadows.isEmpty()) return null

        return TextShadowProperty(shadows)
    }

    private fun parseShadow(shadowStr: String): TextShadowProperty.Shadow? {
        // Shadow format: <offset-x> <offset-y> <blur-radius>? <color>?
        val parts = shadowStr.split(Regex("\\s+"))
        if (parts.size < 2) return null

        var index = 0
        var offsetX: app.irmodels.IRLength? = null
        var offsetY: app.irmodels.IRLength? = null
        var blurRadius: app.irmodels.IRLength? = null
        var color: app.irmodels.IRColor? = null

        // Parse offset-x and offset-y (required)
        offsetX = LengthParser.parse(parts[index]) ?: return null
        index++

        offsetY = LengthParser.parse(parts[index]) ?: return null
        index++

        // Parse optional blur-radius and color
        while (index < parts.size) {
            val part = parts[index]

            // Try to parse as length (blur-radius)
            val length = LengthParser.parse(part)
            if (length != null && blurRadius == null) {
                blurRadius = length
                index++
                continue
            }

            // Try to parse as color
            val parsedColor = ColorParser.parse(part)
            if (parsedColor != null) {
                color = parsedColor
                index++
                continue
            }

            // If we can't parse it, might be a multi-word color like "rgb(255, 0, 0)"
            // Try to parse the rest as a color
            val remainingParts = parts.drop(index).joinToString(" ")
            color = ColorParser.parse(remainingParts)
            break
        }

        return TextShadowProperty.Shadow(
            offsetX = offsetX,
            offsetY = offsetY,
            blurRadius = blurRadius,
            color = color
        )
    }
}
