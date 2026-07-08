package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.AnimationDurationProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords
import app.parsing.css.properties.primitiveParsers.TimeParser

object AnimationDurationPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return AnimationDurationProperty(
                AnimationDurationProperty.AnimationDurationValue.Keyword(trimmed.lowercase())
            )
        }

        // Handle expressions (var(), env(), calc(), etc.)
        if (ExpressionDetector.containsExpression(trimmed)) {
            return AnimationDurationProperty(
                AnimationDurationProperty.AnimationDurationValue.Expression(trimmed)
            )
        }

        // Parse as comma-separated time values
        val durations = trimmed.split(Regex("\\s*,\\s*")).mapNotNull { part ->
            TimeParser.parse(part.trim())
        }

        // Fall back to Expression for unparseable values instead of returning null
        if (durations.isEmpty()) {
            return AnimationDurationProperty(
                AnimationDurationProperty.AnimationDurationValue.Expression(trimmed)
            )
        }

        return AnimationDurationProperty(
            AnimationDurationProperty.AnimationDurationValue.Durations(durations)
        )
    }
}
