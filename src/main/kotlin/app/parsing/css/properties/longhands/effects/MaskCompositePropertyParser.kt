package app.parsing.css.properties.longhands.effects

import app.irmodels.properties.effects.MaskCompositeProperty
import app.irmodels.properties.effects.MaskCompositeValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for the `mask-composite` CSS property.
 *
 * Accepts:
 * - add
 * - subtract
 * - intersect
 * - exclude
 * - Global keywords (inherit, initial, unset, revert, revert-layer)
 * - Dynamic values: var(), env(), calc()
 */
object MaskCompositePropertyParser : PropertyParser {

    override fun parse(value: String): MaskCompositeProperty {
        val trimmed = value.trim().lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return MaskCompositeProperty(MaskCompositeValue.Keyword(trimmed))
        }

        // Handle CSS functions (var, env, calc, etc.)
        if (ExpressionDetector.containsExpression(trimmed)) {
            return MaskCompositeProperty(MaskCompositeValue.Raw(value.trim()))
        }

        val compositeValue = when (trimmed) {
            "add" -> MaskCompositeValue.Add
            "subtract" -> MaskCompositeValue.Subtract
            "intersect" -> MaskCompositeValue.Intersect
            "exclude" -> MaskCompositeValue.Exclude
            else -> MaskCompositeValue.Raw(value.trim())
        }

        return MaskCompositeProperty(compositeValue)
    }
}
