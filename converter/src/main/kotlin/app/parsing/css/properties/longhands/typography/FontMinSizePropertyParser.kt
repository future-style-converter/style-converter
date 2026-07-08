package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontMinSizeProperty
import app.irmodels.properties.typography.FontMinSizeValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object FontMinSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val minSizeValue = if (trimmed == "none") {
            FontMinSizeValue.None
        } else {
            val length = LengthParser.parse(trimmed) ?: return null
            FontMinSizeValue.Length(length)
        }

        return FontMinSizeProperty(minSizeValue)
    }
}
