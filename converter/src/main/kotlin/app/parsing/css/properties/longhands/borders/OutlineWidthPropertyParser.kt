package app.parsing.css.properties.longhands.borders

import app.irmodels.IRProperty
import app.irmodels.properties.borders.OutlineWidthProperty
import app.irmodels.properties.borders.BorderWidthProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object OutlineWidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val width = when (trimmed) {
            "thin" -> BorderWidthProperty.BorderWidth.Keyword(BorderWidthProperty.BorderWidthKeyword.THIN)
            "medium" -> BorderWidthProperty.BorderWidth.Keyword(BorderWidthProperty.BorderWidthKeyword.MEDIUM)
            "thick" -> BorderWidthProperty.BorderWidth.Keyword(BorderWidthProperty.BorderWidthKeyword.THICK)
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                BorderWidthProperty.BorderWidth.Length(length)
            }
        }
        return OutlineWidthProperty(width)
    }
}
