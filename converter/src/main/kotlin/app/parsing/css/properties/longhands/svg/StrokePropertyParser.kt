package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.StrokeProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ColorParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object StrokePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        // 1. Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return StrokeProperty(StrokeProperty.StrokeValue.Keyword(trimmed))
        }

        // 2. Handle var() and env() expressions
        if (ExpressionDetector.startsWithExpression(trimmed)) {
            return StrokeProperty(StrokeProperty.StrokeValue.Raw(trimmed))
        }

        // 3. Handle "none" keyword
        if (trimmed == "none") {
            return StrokeProperty(StrokeProperty.StrokeValue.None())
        }

        // 4. Handle context-fill and context-stroke
        if (trimmed == "context-fill") {
            return StrokeProperty(StrokeProperty.StrokeValue.ContextFill())
        }
        if (trimmed == "context-stroke") {
            return StrokeProperty(StrokeProperty.StrokeValue.ContextStroke())
        }

        // 5. Handle url() references with optional fallback
        if (trimmed.startsWith("url(")) {
            val urlPattern = """url\(['"]?([^'")\s]+)['"]?\)(?:\s+(.+))?""".toRegex()
            val match = urlPattern.matchEntire(trimmed)
            if (match != null) {
                val url = match.groupValues[1]
                val fallback = match.groupValues.getOrNull(2)?.trim()?.let { ColorParser.parse(it) }
                return StrokeProperty(StrokeProperty.StrokeValue.UrlReference(url, fallback))
            }
        }

        // 6. Try parsing as color
        val color = ColorParser.parse(trimmed)
        if (color != null) {
            return StrokeProperty(StrokeProperty.StrokeValue.ColorValue(color))
        }

        // 7. Fall back to raw for unparseable values
        return StrokeProperty(StrokeProperty.StrokeValue.Raw(trimmed))
    }
}
