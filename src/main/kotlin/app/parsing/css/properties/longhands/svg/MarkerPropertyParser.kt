package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.MarkerProperty
import app.irmodels.properties.svg.MarkerValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.UrlParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object MarkerPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return MarkerProperty(MarkerValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return MarkerProperty(MarkerValue.Raw(trimmed))
        }

        // Handle "none" keyword
        if (lower == "none") {
            return MarkerProperty(MarkerValue.None)
        }

        // Parse url()
        val url = UrlParser.parse(trimmed)
        if (url != null) {
            return MarkerProperty(MarkerValue.Url(url))
        }

        return MarkerProperty(MarkerValue.Raw(trimmed))
    }
}
