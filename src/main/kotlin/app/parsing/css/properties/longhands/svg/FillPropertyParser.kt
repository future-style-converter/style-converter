package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.FillProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ColorParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object FillPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Keywords
        if (lower == "none") {
            return FillProperty(FillProperty.FillValue.None())
        }
        if (lower == "context-fill") {
            return FillProperty(FillProperty.FillValue.ContextFill())
        }
        if (lower == "context-stroke") {
            return FillProperty(FillProperty.FillValue.ContextStroke())
        }
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return FillProperty(FillProperty.FillValue.Keyword(lower))
        }

        // URL reference (gradient, pattern, etc.)
        if (lower.startsWith("url(")) {
            val urlEnd = trimmed.indexOf(')')
            if (urlEnd != -1) {
                val url = trimmed.substring(4, urlEnd).trim().removeSurrounding("\"").removeSurrounding("'")
                val remaining = trimmed.substring(urlEnd + 1).trim()
                val fallback = if (remaining.isNotEmpty()) ColorParser.parse(remaining) else null
                return FillProperty(FillProperty.FillValue.UrlReference(url, fallback))
            }
        }

        // Try to parse as color
        val color = ColorParser.parse(value)
        if (color != null) {
            return FillProperty(color)
        }

        return null
    }
}
