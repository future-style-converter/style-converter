package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.AnimationCompositionProperty
import app.irmodels.properties.animations.AnimationCompositionValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parses the CSS `animation-composition` property.
 *
 * Syntax: [ replace | add | accumulate ]#
 *
 * Examples:
 * - "replace" → REPLACE
 * - "add" → ADD
 * - "replace, add, accumulate" → List of values
 */
object AnimationCompositionPropertyParser : PropertyParser {
    private val validValues = setOf("replace", "add", "accumulate")

    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return AnimationCompositionProperty(AnimationCompositionValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return AnimationCompositionProperty(AnimationCompositionValue.Raw(trimmed))
        }

        // Check for comma-separated list
        if (lower.contains(",")) {
            val parts = lower.split(",").map { it.trim() }
            if (parts.all { it in validValues }) {
                return AnimationCompositionProperty(AnimationCompositionValue.List(parts))
            }
        }

        // Single value
        val compositionValue = when (lower) {
            "replace" -> AnimationCompositionValue.Replace
            "add" -> AnimationCompositionValue.Add
            "accumulate" -> AnimationCompositionValue.Accumulate
            else -> AnimationCompositionValue.Raw(trimmed)
        }

        return AnimationCompositionProperty(compositionValue)
    }
}
