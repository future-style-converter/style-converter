package app.parsing.css.properties.longhands.effects

import app.irmodels.properties.effects.MaskRepeatProperty
import app.irmodels.properties.effects.MaskRepeatValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for the `mask-repeat` CSS property.
 *
 * Accepts:
 * - Global keywords (inherit, initial, unset, revert, revert-layer)
 * - repeat
 * - repeat-x
 * - repeat-y
 * - no-repeat
 * - space
 * - round
 * - var(), env(), calc() expressions
 */
object MaskRepeatPropertyParser : PropertyParser {
    override fun parse(value: String): MaskRepeatProperty {
        val trimmed = value.trim()
        val lowered = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lowered)) {
            return MaskRepeatProperty(MaskRepeatValue.Keyword(lowered))
        }

        // Check for var() or other complex expressions
        if (ExpressionDetector.containsExpression(lowered)) {
            return MaskRepeatProperty(MaskRepeatValue.Raw(trimmed))
        }

        val repeatValue = when (lowered) {
            "repeat" -> MaskRepeatValue.Repeat
            "repeat-x" -> MaskRepeatValue.RepeatX
            "repeat-y" -> MaskRepeatValue.RepeatY
            "no-repeat" -> MaskRepeatValue.NoRepeat
            "space" -> MaskRepeatValue.Space
            "round" -> MaskRepeatValue.Round
            else -> MaskRepeatValue.Raw(trimmed)
        }

        return MaskRepeatProperty(repeatValue)
    }
}
