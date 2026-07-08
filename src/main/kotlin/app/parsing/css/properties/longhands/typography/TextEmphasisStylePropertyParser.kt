package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextEmphasisStyle
import app.irmodels.properties.typography.TextEmphasisStyleProperty
import app.parsing.css.properties.longhands.PropertyParser

object TextEmphasisStylePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Check for custom string (quoted character)
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            val character = trimmed.substring(1, trimmed.length - 1)
            return TextEmphasisStyleProperty(TextEmphasisStyle.Custom(character))
        }

        // Single keyword
        val singleStyle = parseSingleKeyword(lowered)
        if (singleStyle != null) {
            return TextEmphasisStyleProperty(singleStyle)
        }

        // Combined values like "filled dot", "open circle", etc.
        val parts = lowered.split(Regex("\\s+"))
        if (parts.size == 2) {
            val fill = parts.find { it in setOf("filled", "open") }
            val shape = parts.find { it in setOf("dot", "circle", "double-circle", "triangle", "sesame") }

            if (fill != null && shape != null) {
                val combined = "$fill $shape".replace(" ", "-")
                return when (combined) {
                    "filled-dot" -> TextEmphasisStyleProperty(TextEmphasisStyle.FilledDot)
                    "open-dot" -> TextEmphasisStyleProperty(TextEmphasisStyle.OpenDot)
                    "filled-circle" -> TextEmphasisStyleProperty(TextEmphasisStyle.FilledCircle)
                    "open-circle" -> TextEmphasisStyleProperty(TextEmphasisStyle.OpenCircle)
                    "filled-double-circle" -> TextEmphasisStyleProperty(TextEmphasisStyle.FilledDoubleCircle)
                    "open-double-circle" -> TextEmphasisStyleProperty(TextEmphasisStyle.OpenDoubleCircle)
                    "filled-triangle" -> TextEmphasisStyleProperty(TextEmphasisStyle.FilledTriangle)
                    "open-triangle" -> TextEmphasisStyleProperty(TextEmphasisStyle.OpenTriangle)
                    "filled-sesame" -> TextEmphasisStyleProperty(TextEmphasisStyle.FilledSesame)
                    "open-sesame" -> TextEmphasisStyleProperty(TextEmphasisStyle.OpenSesame)
                    else -> null
                }
            }
        }

        return null
    }

    private fun parseSingleKeyword(v: String): TextEmphasisStyle? = when (v) {
        "none" -> TextEmphasisStyle.None
        "filled" -> TextEmphasisStyle.Filled
        "open" -> TextEmphasisStyle.Open
        "dot" -> TextEmphasisStyle.Dot
        "circle" -> TextEmphasisStyle.Circle
        "double-circle" -> TextEmphasisStyle.DoubleCircle
        "triangle" -> TextEmphasisStyle.Triangle
        "sesame" -> TextEmphasisStyle.Sesame
        else -> null
    }
}
