package app.parsing.css.properties.longhands.shapes

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.shapes.ShapePaddingProperty
import app.irmodels.properties.shapes.ShapePaddingValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object ShapePaddingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        val paddingValue = when {
            trimmed.endsWith("%") -> {
                val percent = trimmed.removeSuffix("%").toDoubleOrNull() ?: return null
                ShapePaddingValue.Percentage(IRPercentage(percent))
            }
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                ShapePaddingValue.Length(length)
            }
        }

        return ShapePaddingProperty(paddingValue)
    }
}
