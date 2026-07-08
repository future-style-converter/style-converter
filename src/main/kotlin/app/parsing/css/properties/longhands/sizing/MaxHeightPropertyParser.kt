package app.parsing.css.properties.longhands.sizing

import app.irmodels.*
import app.irmodels.properties.spacing.MaxHeightProperty
import app.irmodels.properties.spacing.MaxWidthProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object MaxHeightPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val maxValue = when {
            trimmed == "none" -> MaxWidthProperty.MaxValue.None()
            trimmed == "min-content" -> MaxWidthProperty.MaxValue.MinContent()
            trimmed == "max-content" -> MaxWidthProperty.MaxValue.MaxContent()
            trimmed.startsWith("fit-content(") && trimmed.endsWith(")") -> {
                val sizeStr = trimmed.substring(12, trimmed.length - 1)
                val size = LengthParser.parse(sizeStr)
                MaxWidthProperty.MaxValue.FitContent(size)
            }
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    MaxWidthProperty.MaxValue.PercentageValue(IRPercentage(length.value))
                } else {
                    MaxWidthProperty.MaxValue.LengthValue(length)
                }
            }
        }
        return MaxHeightProperty(maxValue)
    }
}
