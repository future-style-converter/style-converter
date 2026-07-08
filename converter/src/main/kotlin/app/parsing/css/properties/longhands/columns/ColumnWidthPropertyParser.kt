package app.parsing.css.properties.longhands.columns

import app.irmodels.IRProperty
import app.irmodels.properties.columns.ColumnWidthProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object ColumnWidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val width = when {
            trimmed == "auto" -> ColumnWidthProperty.ColumnWidth.Auto()
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                ColumnWidthProperty.ColumnWidth.LengthValue(length)
            }
        }

        return ColumnWidthProperty(width)
    }
}
