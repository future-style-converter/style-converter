package app.parsing.css.properties.longhands.effects

import app.irmodels.properties.effects.MaskModeProperty
import app.irmodels.properties.effects.MaskModeValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for the `mask-mode` CSS property.
 *
 * Accepts:
 * - alpha, luminance, match-source
 * - Global keywords: inherit, initial, unset, revert, revert-layer
 * - CSS expressions: var(), env(), calc(), etc.
 */
object MaskModePropertyParser : PropertyParser {

    override fun parse(value: String): MaskModeProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return MaskModeProperty(MaskModeValue.Keyword(lower))
        }

        // Handle CSS expressions
        if (ExpressionDetector.startsWithExpression(trimmed)) {
            return MaskModeProperty(MaskModeValue.Raw(trimmed))
        }

        // Parse mask-mode values
        val modeValue = when (lower) {
            "alpha" -> MaskModeValue.Alpha
            "luminance" -> MaskModeValue.Luminance
            "match-source" -> MaskModeValue.MatchSource
            else -> MaskModeValue.Raw(trimmed)
        }

        return MaskModeProperty(modeValue)
    }
}
