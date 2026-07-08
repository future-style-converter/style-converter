package app.parsing.css.properties.longhands.print

import app.irmodels.IRProperty
import app.irmodels.properties.print.BleedProperty
import app.irmodels.properties.print.BleedValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object BleedPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        return when (trimmed) {
            "auto" -> BleedProperty(BleedValue.Auto)
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                BleedProperty(BleedValue.Length(length))
            }
        }
    }
}
