package app.parsing.css.properties.longhands.svg

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.svg.StrokeDasharrayProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object StrokeDasharrayPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim()
        val lower = normalized.lowercase()

        // Handle "none" keyword
        if (lower == "none") {
            return StrokeDasharrayProperty(StrokeDasharrayProperty.DasharrayValue.None())
        }

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return StrokeDasharrayProperty(StrokeDasharrayProperty.DasharrayValue.Keyword(lower))
        }

        // Parse comma or space separated list
        val parts = normalized.split(Regex("[,\\s]+")).filter { it.isNotBlank() }

        // Try parsing as lengths first
        val lengths = parts.mapNotNull { LengthParser.parse(it) }
        if (lengths.size == parts.size) {
            return StrokeDasharrayProperty(StrokeDasharrayProperty.DasharrayValue.Lengths(lengths))
        }

        // Try parsing as pure numbers (SVG allows unitless numbers)
        val numbers = parts.mapNotNull { it.toDoubleOrNull()?.let { n -> IRNumber(n) } }
        if (numbers.size == parts.size) {
            return StrokeDasharrayProperty(StrokeDasharrayProperty.DasharrayValue.Numbers(numbers))
        }

        // Store as raw mixed value
        return StrokeDasharrayProperty(StrokeDasharrayProperty.DasharrayValue.Mixed(normalized))
    }
}
