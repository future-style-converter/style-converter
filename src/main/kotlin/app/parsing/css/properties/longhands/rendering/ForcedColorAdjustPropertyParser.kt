package app.parsing.css.properties.longhands.rendering

import app.irmodels.IRProperty
import app.irmodels.properties.rendering.ForcedColorAdjustProperty
import app.irmodels.properties.rendering.ForcedColorAdjustValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object ForcedColorAdjustPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return ForcedColorAdjustProperty(ForcedColorAdjustValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return ForcedColorAdjustProperty(ForcedColorAdjustValue.Raw(trimmed))
        }

        val v = when (lower) {
            "auto" -> ForcedColorAdjustValue.Auto
            "none" -> ForcedColorAdjustValue.None
            "preserve-parent-color" -> ForcedColorAdjustValue.PreserveParentColor
            else -> ForcedColorAdjustValue.Raw(trimmed)
        }
        return ForcedColorAdjustProperty(v)
    }
}
