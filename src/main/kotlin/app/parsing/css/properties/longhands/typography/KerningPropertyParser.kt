package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.KerningProperty
import app.irmodels.properties.typography.KerningValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object KerningPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val kerningValue = when {
            trimmed == "auto" -> KerningValue.Auto
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                KerningValue.Length(length)
            }
        }

        return KerningProperty(kerningValue)
    }
}
