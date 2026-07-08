package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.irmodels.MaskBorderRepeatValue
import app.irmodels.properties.effects.MaskBorderRepeatProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object MaskBorderRepeatPropertyParser : PropertyParser {
    private val validRepeatValues = setOf("stretch", "repeat", "round", "space")

    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return MaskBorderRepeatProperty(MaskBorderRepeatValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return MaskBorderRepeatProperty(MaskBorderRepeatValue.Raw(trimmed))
        }

        // Parse one or two values
        val parts = lower.split(Regex("\\s+"))

        val v = when {
            parts.size == 2 && parts[0] in validRepeatValues && parts[1] in validRepeatValues -> {
                MaskBorderRepeatValue.TwoValue(parts[0], parts[1])
            }
            parts.size == 1 -> when (lower) {
                "stretch" -> MaskBorderRepeatValue.Stretch
                "repeat" -> MaskBorderRepeatValue.Repeat
                "round" -> MaskBorderRepeatValue.Round
                "space" -> MaskBorderRepeatValue.Space
                else -> MaskBorderRepeatValue.Raw(trimmed)
            }
            else -> MaskBorderRepeatValue.Raw(trimmed)
        }
        return MaskBorderRepeatProperty(v)
    }
}
