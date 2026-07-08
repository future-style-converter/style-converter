package app.parsing.css.properties.longhands.sizing

import app.irmodels.*
import app.irmodels.properties.sizing.MaxBlockSizeProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object MaxBlockSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val sizeValue = when (trimmed) {
            "none" -> SizeValue.None
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                if (length.unit == IRLength.LengthUnit.PERCENT) {
                    SizeValue.PercentageValue(IRPercentage(length.value))
                } else {
                    SizeValue.LengthValue(length)
                }
            }
        }
        return MaxBlockSizeProperty(sizeValue)
    }
}
