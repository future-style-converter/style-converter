package app.parsing.css.properties.longhands.interactions

import app.irmodels.IRProperty
import app.irmodels.properties.interactions.CaretProperty
import app.irmodels.properties.interactions.CaretShapeValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ColorParser

object CaretPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split("\\s+".toRegex())
        var color: app.irmodels.IRColor? = null
        var shape: CaretShapeValue? = null

        for (part in parts) {
            val lower = part.lowercase()
            when (lower) {
                "auto" -> shape = CaretShapeValue.AUTO
                "bar" -> shape = CaretShapeValue.BAR
                "block" -> shape = CaretShapeValue.BLOCK
                "underscore" -> shape = CaretShapeValue.UNDERSCORE
                else -> {
                    val parsedColor = ColorParser.parse(part)
                    if (parsedColor != null) {
                        color = parsedColor
                    }
                }
            }
        }

        if (color == null && shape == null) return null
        return CaretProperty(color, shape)
    }
}
