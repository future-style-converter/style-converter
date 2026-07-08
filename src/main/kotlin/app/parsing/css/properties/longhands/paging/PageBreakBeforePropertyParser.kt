package app.parsing.css.properties.longhands.paging

import app.irmodels.IRProperty
import app.irmodels.properties.paging.PageBreakBeforeProperty
import app.irmodels.properties.paging.PageBreakValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object PageBreakBeforePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return PageBreakBeforeProperty(PageBreakValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return PageBreakBeforeProperty(PageBreakValue.Raw(trimmed))
        }

        val breakValue = when (lower) {
            "auto" -> PageBreakValue.Auto
            "always" -> PageBreakValue.Always
            "avoid" -> PageBreakValue.Avoid
            "left" -> PageBreakValue.Left
            "right" -> PageBreakValue.Right
            "recto" -> PageBreakValue.Recto
            "verso" -> PageBreakValue.Verso
            else -> PageBreakValue.Raw(trimmed)
        }
        return PageBreakBeforeProperty(breakValue)
    }
}
