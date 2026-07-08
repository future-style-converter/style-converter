package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextSpaceTrimProperty
import app.irmodels.properties.typography.TextSpaceTrimValue
import app.parsing.css.properties.longhands.PropertyParser

object TextSpaceTrimPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle space-separated values (can combine multiple)
        val parts = trimmed.split(Regex("\\s+"))
        val values = mutableListOf<TextSpaceTrimValue>()

        for (part in parts) {
            val trimValue = when (part) {
                "none" -> TextSpaceTrimValue.NONE
                "trim-start" -> TextSpaceTrimValue.TRIM_START
                "space-first" -> TextSpaceTrimValue.SPACE_FIRST
                "trim-end" -> TextSpaceTrimValue.TRIM_END
                "space-all" -> TextSpaceTrimValue.SPACE_ALL
                else -> return null
            }
            values.add(trimValue)
        }

        if (values.isEmpty()) return null

        return TextSpaceTrimProperty(values)
    }
}
