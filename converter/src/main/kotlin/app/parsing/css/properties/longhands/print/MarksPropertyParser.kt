package app.parsing.css.properties.longhands.print

import app.irmodels.IRProperty
import app.irmodels.properties.print.MarksProperty
import app.irmodels.properties.print.MarksValue
import app.parsing.css.properties.longhands.PropertyParser

object MarksPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        if (trimmed == "none") {
            return MarksProperty(listOf(MarksValue.NONE))
        }

        val parts = trimmed.split(Regex("\\s+"))
        val values = parts.mapNotNull { part ->
            when (part) {
                "crop" -> MarksValue.CROP
                "cross" -> MarksValue.CROSS
                else -> null
            }
        }

        return if (values.isNotEmpty()) MarksProperty(values) else null
    }
}
