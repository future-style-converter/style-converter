package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextDecorationSkipProperty
import app.irmodels.properties.typography.TextDecorationSkipValue
import app.parsing.css.properties.longhands.PropertyParser

object TextDecorationSkipPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "none") {
            return TextDecorationSkipProperty(listOf(TextDecorationSkipValue.NONE))
        }

        val parts = trimmed.split(Regex("\\s+"))
        val values = parts.mapNotNull { part ->
            when (part) {
                "objects" -> TextDecorationSkipValue.OBJECTS
                "spaces" -> TextDecorationSkipValue.SPACES
                "leading-spaces" -> TextDecorationSkipValue.LEADING_SPACES
                "trailing-spaces" -> TextDecorationSkipValue.TRAILING_SPACES
                "edges" -> TextDecorationSkipValue.EDGES
                "box-decoration" -> TextDecorationSkipValue.BOX_DECORATION
                else -> null
            }
        }

        return if (values.isNotEmpty()) TextDecorationSkipProperty(values) else null
    }
}
