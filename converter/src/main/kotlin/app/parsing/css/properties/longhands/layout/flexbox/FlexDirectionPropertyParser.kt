package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.layout.flexbox.FlexDirectionProperty
import app.parsing.css.properties.longhands.PropertyParser

object FlexDirectionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val direction = when (trimmed) {
            "row" -> FlexDirectionProperty.FlexDirection.ROW
            "row-reverse" -> FlexDirectionProperty.FlexDirection.ROW_REVERSE
            "column" -> FlexDirectionProperty.FlexDirection.COLUMN
            "column-reverse" -> FlexDirectionProperty.FlexDirection.COLUMN_REVERSE
            else -> return null
        }
        return FlexDirectionProperty(direction)
    }
}
