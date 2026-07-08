package app.parsing.css.properties.longhands.table

import app.irmodels.IRProperty
import app.irmodels.properties.table.CaptionSideProperty
import app.irmodels.properties.table.CaptionSideValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object CaptionSidePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return CaptionSideProperty(CaptionSideValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return CaptionSideProperty(CaptionSideValue.Raw(trimmed))
        }

        val side = when (lower) {
            "top" -> CaptionSideValue.Top
            "bottom" -> CaptionSideValue.Bottom
            "block-start" -> CaptionSideValue.BlockStart
            "block-end" -> CaptionSideValue.BlockEnd
            "inline-start" -> CaptionSideValue.InlineStart
            "inline-end" -> CaptionSideValue.InlineEnd
            else -> CaptionSideValue.Raw(trimmed)
        }
        return CaptionSideProperty(side)
    }
}
