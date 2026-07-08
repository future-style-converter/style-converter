package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.WordBreakProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object WordBreakPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lowercase = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lowercase)) {
            return WordBreakProperty.Keyword(lowercase)
        }

        // Handle var() and env() expressions
        if (ExpressionDetector.startsWithExpression(lowercase)) {
            return WordBreakProperty.Raw(trimmed)
        }

        // Handle specific word-break values
        val wordBreakValue = when (lowercase) {
            "normal" -> WordBreakProperty.WordBreakValue.NORMAL
            "break-all" -> WordBreakProperty.WordBreakValue.BREAK_ALL
            "keep-all" -> WordBreakProperty.WordBreakValue.KEEP_ALL
            "break-word" -> WordBreakProperty.WordBreakValue.BREAK_WORD
            "auto-phrase" -> WordBreakProperty.WordBreakValue.AUTO_PHRASE
            else -> return WordBreakProperty.Raw(trimmed)
        }
        return WordBreakProperty.WordBreak(wordBreakValue)
    }
}
