package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextEmphasisProperty
import app.irmodels.properties.typography.TextEmphasisStyle
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ColorParser

object TextEmphasisPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return null

        var style: TextEmphasisStyle = TextEmphasisStyle.None
        var color = ColorParser.parse(parts.last())

        for (part in parts) {
            val lowered = part.lowercase()
            val parsedStyle: TextEmphasisStyle? = when (lowered) {
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
            if (parsedStyle != null) {
                style = parsedStyle
            }
        }

        return TextEmphasisProperty(style, color)
    }
}
