package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.TransitionBehaviorProperty
import app.irmodels.properties.animations.TransitionBehaviorValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parses the CSS `transition-behavior` property.
 *
 * Syntax: [ normal | allow-discrete ]#
 */
object TransitionBehaviorPropertyParser : PropertyParser {

    private val validValues = setOf("normal", "allow-discrete")

    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return TransitionBehaviorProperty(TransitionBehaviorValue.Keyword(lower))
        }

        // Handle expressions (var(), env(), calc(), etc.)
        if (ExpressionDetector.containsExpression(trimmed)) {
            return TransitionBehaviorProperty(TransitionBehaviorValue.Raw(trimmed))
        }

        // Check for comma-separated list
        if (lower.contains(",")) {
            val parts = lower.split(",").map { it.trim() }
            if (parts.all { it in validValues }) {
                return TransitionBehaviorProperty(TransitionBehaviorValue.List(parts))
            }
        }

        // Single value
        val behaviorValue = when (lower) {
            "normal" -> TransitionBehaviorValue.Normal
            "allow-discrete" -> TransitionBehaviorValue.AllowDiscrete
            else -> TransitionBehaviorValue.Raw(trimmed)
        }

        return TransitionBehaviorProperty(behaviorValue)
    }
}
