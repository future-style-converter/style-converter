package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextUnderlinePositionProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `text-underline-position` property.
 *
 * Syntax: auto | from-font | [ under || [ left | right ] ]
 */
object TextUnderlinePositionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle single keyword values
        if (trimmed == "auto") {
            return TextUnderlinePositionProperty(listOf(TextUnderlinePositionProperty.Position.AUTO))
        }

        // Parse space-separated values (can combine)
        val positions = trimmed.split(Regex("\\s+")).mapNotNull { parsePosition(it) }
        if (positions.isEmpty()) return null

        // Validate combinations: auto and from-font cannot be combined with other values
        if (positions.contains(TextUnderlinePositionProperty.Position.AUTO) && positions.size > 1) {
            return null
        }
        if (positions.contains(TextUnderlinePositionProperty.Position.FROM_FONT) && positions.size > 1) {
            return null
        }

        // Cannot have both left and right
        if (positions.contains(TextUnderlinePositionProperty.Position.LEFT) &&
            positions.contains(TextUnderlinePositionProperty.Position.RIGHT)) {
            return null
        }

        return TextUnderlinePositionProperty(positions)
    }

    private fun parsePosition(value: String): TextUnderlinePositionProperty.Position? {
        return when (value) {
            "auto" -> TextUnderlinePositionProperty.Position.AUTO
            "from-font" -> TextUnderlinePositionProperty.Position.FROM_FONT
            "under" -> TextUnderlinePositionProperty.Position.UNDER
            "left" -> TextUnderlinePositionProperty.Position.LEFT
            "right" -> TextUnderlinePositionProperty.Position.RIGHT
            else -> null
        }
    }
}
