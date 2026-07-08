package app.parsing.css.properties.longhands.borders

import app.irmodels.BorderRadiusValue
import app.irmodels.IRProperty
import app.irmodels.properties.borders.BorderStartStartRadiusProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object BorderStartStartRadiusPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return null

        val horizontal = LengthParser.parse(parts[0]) ?: return null
        val vertical = if (parts.size > 1) LengthParser.parse(parts[1]) else null

        return BorderStartStartRadiusProperty(
            BorderRadiusValue.Length(horizontal),
            vertical?.let { BorderRadiusValue.Length(it) }
        )
    }
}
