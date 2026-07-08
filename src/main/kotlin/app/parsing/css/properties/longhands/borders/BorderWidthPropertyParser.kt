package app.parsing.css.properties.longhands.borders

import app.irmodels.IRProperty
import app.irmodels.properties.borders.BorderWidthProperty
import app.irmodels.properties.borders.BorderWidthProperty.BorderWidth
import app.irmodels.properties.borders.BorderWidthProperty.BorderWidthKeyword
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object BorderWidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Check for keyword values
        val keyword = when (trimmed) {
            "thin" -> BorderWidthKeyword.THIN
            "medium" -> BorderWidthKeyword.MEDIUM
            "thick" -> BorderWidthKeyword.THICK
            else -> null
        }

        if (keyword != null) {
            return BorderWidthProperty(BorderWidth.Keyword(keyword))
        }

        // Try to parse as length
        val length = LengthParser.parse(trimmed)
        if (length != null) {
            return BorderWidthProperty(BorderWidth.Length(length))
        }

        return null
    }
}
