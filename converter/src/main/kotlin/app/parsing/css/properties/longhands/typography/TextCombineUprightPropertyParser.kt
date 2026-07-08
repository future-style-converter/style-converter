package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextCombineUprightProperty
import app.irmodels.properties.typography.TextCombineUprightValue
import app.parsing.css.properties.longhands.PropertyParser

object TextCombineUprightPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val combineValue = when {
            trimmed == "none" -> TextCombineUprightValue.None
            trimmed == "all" -> TextCombineUprightValue.All
            trimmed.startsWith("digits") -> {
                // Parse "digits 2", "digits 3", "digits 4"
                val parts = trimmed.split(Regex("\\s+"))
                if (parts.size == 2) {
                    val count = parts[1].toIntOrNull()
                    if (count != null && count in 2..4) {
                        TextCombineUprightValue.Digits(count)
                    } else {
                        return null
                    }
                } else {
                    // Just "digits" defaults to 2
                    TextCombineUprightValue.Digits(2)
                }
            }
            else -> return null
        }

        return TextCombineUprightProperty(combineValue)
    }
}
