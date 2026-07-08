package app.parsing.css.properties.longhands.sizing

import app.irmodels.*
import app.irmodels.properties.spacing.WidthProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object WidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        val widthValue = when {
            lower == "auto" -> WidthProperty.WidthValue.Auto()
            lower == "min-content" -> WidthProperty.WidthValue.MinContent()
            lower == "max-content" -> WidthProperty.WidthValue.MaxContent()
            lower.startsWith("fit-content(") && lower.endsWith(")") -> {
                val sizeStr = lower.substring(12, lower.length - 1)
                val size = LengthParser.parse(sizeStr)
                WidthProperty.WidthValue.FitContent(size)
            }
            lower.startsWith("anchor-size(") && lower.endsWith(")") -> {
                parseAnchorSize(trimmed)
            }
            // Handle calc(), clamp(), min(), max(), var(), env() and math function expressions
            LengthParser.isExpression(lower) -> WidthProperty.WidthValue.Expression(trimmed)
            else -> {
                val length = LengthParser.parse(lower) ?: return null
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    WidthProperty.WidthValue.PercentageValue(IRPercentage(length.value))
                } else {
                    WidthProperty.WidthValue.LengthValue(length)
                }
            }
        }

        return WidthProperty(widthValue)
    }

    private fun parseAnchorSize(value: String): WidthProperty.WidthValue.AnchorSize {
        // Formats: anchor-size(dimension) or anchor-size(--name dimension)
        val content = value.substringAfter("anchor-size(").substringBefore(")").trim()
        val tokens = content.split(Regex("\\s+"))
        return if (tokens.size >= 2 && tokens[0].startsWith("--")) {
            WidthProperty.WidthValue.AnchorSize(anchorName = tokens[0], dimension = tokens[1])
        } else {
            WidthProperty.WidthValue.AnchorSize(anchorName = null, dimension = tokens.getOrElse(0) { "width" })
        }
    }
}
