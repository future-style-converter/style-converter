package app.parsing.css.properties.longhands.sizing

import app.irmodels.*
import app.irmodels.properties.sizing.MinInlineSizeProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object MinInlineSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val sizeValue = when (trimmed) {
            "auto" -> SizeValue.Auto
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    SizeValue.PercentageValue(IRPercentage(length.value))
                } else {
                    SizeValue.LengthValue(length)
                }
            }
        }
        return MinInlineSizeProperty(sizeValue)
    }
}
