package app.parsing.css.properties.longhands.animations

import app.irmodels.*
import app.irmodels.properties.animations.AnimationRangeProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for `animation-range` property.
 *
 * Syntax: <animation-range-start> <animation-range-end>?
 * Where each can be: normal | <length-percentage> | <timeline-range-name> <length-percentage>?
 *
 * Examples:
 * - "0% 100%" -> start: 0%, end: 100%
 * - "entry 0% cover 100%" -> start: entry 0%, end: cover 100%
 * - "normal" -> normal
 */
object AnimationRangePropertyParser : PropertyParser {
    override fun parse(value: String): AnimationRangeProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return AnimationRangeProperty(AnimationRangeValue.Keyword(lower), null)
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return AnimationRangeProperty(AnimationRangeValue.Raw(trimmed), null)
        }

        if (lower == "normal") {
            return AnimationRangeProperty(AnimationRangeValue.Keyword("normal"), null)
        }

        // Try parsing as two percentage values (e.g., "0% 100%")
        val parts = lower.split(Regex("\\s+"))

        // Handle simple percentage pairs
        if (parts.size == 2) {
            val start = parseRangeValue(parts[0])
            val end = parseRangeValue(parts[1])
            return AnimationRangeProperty(start, end)
        }

        // Handle timeline-range-name format (e.g., "entry 0% cover 100%")
        if (parts.size == 4) {
            val start = AnimationRangeValue.Keyword("${parts[0]} ${parts[1]}")
            val end = AnimationRangeValue.Keyword("${parts[2]} ${parts[3]}")
            return AnimationRangeProperty(start, end)
        }

        // Single value
        if (parts.size == 1) {
            val start = parseRangeValue(parts[0])
            return AnimationRangeProperty(start, null)
        }

        return AnimationRangeProperty(AnimationRangeValue.Raw(trimmed), null)
    }

    private fun parseRangeValue(value: String): AnimationRangeValue {
        // Try percentage first
        PercentageParser.parse(value)?.let {
            return AnimationRangeValue.Percentage(it)
        }

        // Try length
        LengthParser.parse(value)?.let {
            return AnimationRangeValue.Length(it)
        }

        // Keywords
        if (value in setOf("normal", "cover", "contain", "entry", "exit", "entry-crossing", "exit-crossing")) {
            return AnimationRangeValue.Keyword(value)
        }

        return AnimationRangeValue.Raw(value)
    }
}
