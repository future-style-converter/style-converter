package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.HangingPunctuationProperty
import app.irmodels.properties.typography.HangingPunctuationValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `hanging-punctuation` property.
 *
 * Values: none | [ first || [ force-end | allow-end ] || last ]
 * Can combine: first, last, force-end, allow-end
 */
object HangingPunctuationPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle 'none' keyword
        if (trimmed == "none") {
            return HangingPunctuationProperty(listOf(HangingPunctuationValue.NONE))
        }

        // Parse space-separated values
        val parts = trimmed.split(Regex("""\s+"""))
        val values = mutableListOf<HangingPunctuationValue>()

        for (part in parts) {
            val hangingValue = when (part) {
                "first" -> HangingPunctuationValue.FIRST
                "last" -> HangingPunctuationValue.LAST
                "force-end" -> HangingPunctuationValue.FORCE_END
                "allow-end" -> HangingPunctuationValue.ALLOW_END
                else -> return null
            }
            values.add(hangingValue)
        }

        if (values.isEmpty()) return null

        return HangingPunctuationProperty(values)
    }
}
