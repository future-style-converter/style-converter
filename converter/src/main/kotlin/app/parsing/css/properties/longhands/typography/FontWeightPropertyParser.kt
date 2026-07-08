package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontWeightProperty
import app.parsing.css.properties.longhands.PropertyParser

object FontWeightPropertyParser : PropertyParser {
    private val KEYWORDS = setOf("normal", "bold", "lighter", "bolder")

    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        return when {
            trimmed in KEYWORDS -> FontWeightProperty.fromKeyword(trimmed)
            else -> {
                val numeric = trimmed.toIntOrNull()
                // CSS spec allows 1-1000, common values are 100-900 in steps of 100
                if (numeric != null && numeric in 1..1000) {
                    FontWeightProperty.fromNumeric(numeric)
                } else {
                    null
                }
            }
        }
    }
}
