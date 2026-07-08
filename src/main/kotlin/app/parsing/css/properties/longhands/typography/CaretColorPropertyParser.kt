package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.CaretColorProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ColorParser

object CaretColorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "auto") {
            return CaretColorProperty(CaretColorProperty.CaretColor.Auto())
        }

        val color = ColorParser.parse(value) ?: return null
        return CaretColorProperty(CaretColorProperty.CaretColor.ColorValue(color))
    }
}
