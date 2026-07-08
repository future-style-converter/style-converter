package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextAutospaceProperty
import app.irmodels.properties.typography.TextAutospaceValue
import app.parsing.css.properties.longhands.PropertyParser

object TextAutospacePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Property can have multiple space-separated values
        val parts = trimmed.split(Regex("\\s+"))
        val values = mutableListOf<TextAutospaceValue>()

        for (part in parts) {
            val autospaceValue = when (part) {
                "normal" -> TextAutospaceValue.NORMAL
                "auto" -> TextAutospaceValue.NORMAL // Map auto to NORMAL
                "no-autospace" -> TextAutospaceValue.NO_AUTOSPACE
                "ideograph-alpha" -> TextAutospaceValue.IDEOGRAPH_ALPHA
                "ideograph-numeric" -> TextAutospaceValue.IDEOGRAPH_NUMERIC
                "ideograph-parenthesis" -> TextAutospaceValue.IDEOGRAPH_PARENTHESIS
                else -> return null
            }
            values.add(autospaceValue)
        }

        if (values.isEmpty()) return null

        return TextAutospaceProperty(values)
    }
}
