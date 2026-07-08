package app.parsing.css.properties.longhands.typography

import app.irmodels.*
import app.irmodels.properties.typography.TabSizeProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object TabSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        val number = trimmed.toDoubleOrNull()
        if (number != null) {
            return TabSizeProperty(TabSizeProperty.TabSize.Number(IRNumber(number)))
        }

        val length = LengthParser.parse(trimmed) ?: return null
        return TabSizeProperty(TabSizeProperty.TabSize.LengthValue(length))
    }
}
