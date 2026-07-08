package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontMaxSizeProperty
import app.irmodels.properties.typography.FontMaxSizeValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object FontMaxSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val maxSizeValue = when (trimmed) {
            "none" -> FontMaxSizeValue.None
            "infinity", "infinite" -> FontMaxSizeValue.Infinity
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                FontMaxSizeValue.Length(length)
            }
        }

        return FontMaxSizeProperty(maxSizeValue)
    }
}
