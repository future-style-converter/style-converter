package app.parsing.css.properties.longhands.paging

import app.irmodels.IRProperty
import app.irmodels.properties.paging.PageBreakInsideProperty
import app.irmodels.properties.paging.PageBreakInsideValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object PageBreakInsidePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return PageBreakInsideProperty(PageBreakInsideValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return PageBreakInsideProperty(PageBreakInsideValue.Raw(trimmed))
        }

        val breakValue = when (lower) {
            "auto" -> PageBreakInsideValue.Auto
            "avoid" -> PageBreakInsideValue.Avoid
            else -> PageBreakInsideValue.Raw(trimmed)
        }
        return PageBreakInsideProperty(breakValue)
    }
}
