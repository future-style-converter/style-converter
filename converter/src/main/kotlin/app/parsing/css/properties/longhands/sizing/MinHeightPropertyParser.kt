package app.parsing.css.properties.longhands.sizing

import app.irmodels.*
import app.irmodels.properties.spacing.MinHeightProperty
import app.irmodels.properties.spacing.MinWidthProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object MinHeightPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val minValue = when {
            trimmed == "auto" -> MinWidthProperty.MinMaxValue.Auto()
            trimmed == "min-content" -> MinWidthProperty.MinMaxValue.MinContent()
            trimmed == "max-content" -> MinWidthProperty.MinMaxValue.MaxContent()
            trimmed.startsWith("fit-content(") && trimmed.endsWith(")") -> {
                val sizeStr = trimmed.substring(12, trimmed.length - 1)
                val size = LengthParser.parse(sizeStr)
                MinWidthProperty.MinMaxValue.FitContent(size)
            }
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    MinWidthProperty.MinMaxValue.PercentageValue(IRPercentage(length.value))
                } else {
                    MinWidthProperty.MinMaxValue.LengthValue(length)
                }
            }
        }
        return MinHeightProperty(minValue)
    }
}
