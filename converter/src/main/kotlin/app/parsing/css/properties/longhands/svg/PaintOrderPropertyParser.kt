package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.PaintOrderProperty
import app.irmodels.properties.svg.PaintOrderValue
import app.parsing.css.properties.longhands.PropertyParser

object PaintOrderPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim().lowercase()

        // Handle "normal" keyword
        if (normalized == "normal") {
            return PaintOrderProperty(listOf(PaintOrderValue.NORMAL))
        }

        // Parse space-separated list of keywords
        val parts = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        val values = parts.mapNotNull { part ->
            when (part) {
                "fill" -> PaintOrderValue.FILL
                "stroke" -> PaintOrderValue.STROKE
                "markers" -> PaintOrderValue.MARKERS
                else -> null
            }
        }

        // Return null if any part failed to parse
        if (values.isEmpty() || values.size != parts.size) return null

        return PaintOrderProperty(values)
    }
}
