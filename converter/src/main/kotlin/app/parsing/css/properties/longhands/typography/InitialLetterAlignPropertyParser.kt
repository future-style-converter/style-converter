package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.InitialLetterAlignProperty
import app.irmodels.properties.typography.InitialLetterAlignValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for `initial-letter-align` property.
 *
 * Values: auto | alphabetic | hanging | ideographic
 */
object InitialLetterAlignPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return InitialLetterAlignProperty(InitialLetterAlignValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return InitialLetterAlignProperty(InitialLetterAlignValue.Raw(trimmed))
        }

        val alignment = when (lower) {
            "auto" -> InitialLetterAlignValue.Auto
            "alphabetic" -> InitialLetterAlignValue.Alphabetic
            "hanging" -> InitialLetterAlignValue.Hanging
            "ideographic" -> InitialLetterAlignValue.Ideographic
            "border-box" -> InitialLetterAlignValue.BorderBox
            else -> InitialLetterAlignValue.Raw(trimmed)
        }

        return InitialLetterAlignProperty(alignment)
    }
}
