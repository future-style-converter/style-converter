package app.parsing.css.properties.longhands.typography

import app.irmodels.*
import app.irmodels.properties.typography.FontSize
import app.irmodels.properties.typography.FontSizeProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for the CSS `font-size` property.
 *
 * Normalizes font sizes to pixels when possible. Relative sizes and expressions
 * have `pixels = null` since they require runtime context.
 *
 * ## Supported Values
 * - **Absolute keywords**: xx-small (9px), x-small (10px), small (13px), medium (16px),
 *                         large (18px), x-large (24px), xx-large (32px), xxx-large (48px)
 * - **Relative keywords**: smaller, larger → pixels = null
 * - **Lengths**: px, pt, em, rem, etc. → normalized to pixels (when absolute)
 * - **Percentages**: 120%, 80% → pixels = null
 * - **Expressions**: calc(), clamp(), var() → pixels = null
 * - **Global keywords**: inherit, initial, unset, revert
 *
 * @see FontSize for the dual-storage value type
 * @see FontSizeProperty for the IR property
 */
object FontSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords first
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return FontSizeProperty(FontSize.fromGlobalKeyword(lower))
        }

        // Handle absolute size keywords
        val absoluteSize = parseAbsoluteSize(lower)
        if (absoluteSize != null) {
            return FontSizeProperty(FontSize.fromAbsoluteKeyword(absoluteSize))
        }

        // Handle relative size keywords
        val relativeSize = parseRelativeSize(lower)
        if (relativeSize != null) {
            return FontSizeProperty(FontSize.fromRelativeKeyword(relativeSize))
        }

        // Handle calc(), clamp(), min(), max(), var() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return FontSizeProperty(FontSize.fromExpression(trimmed))
        }

        // Try parsing as length/percentage
        val length = LengthParser.parse(lower) ?: return null
        val fontSize = if (length.unit == IRLength.LengthUnit.PERCENT) {
            FontSize.fromPercentage(IRPercentage(length.value))
        } else {
            FontSize.fromLength(length)
        }

        return FontSizeProperty(fontSize)
    }

    private fun parseAbsoluteSize(value: String): FontSize.AbsoluteSize? = when (value) {
        "xx-small" -> FontSize.AbsoluteSize.XX_SMALL
        "x-small" -> FontSize.AbsoluteSize.X_SMALL
        "small" -> FontSize.AbsoluteSize.SMALL
        "medium" -> FontSize.AbsoluteSize.MEDIUM
        "large" -> FontSize.AbsoluteSize.LARGE
        "x-large" -> FontSize.AbsoluteSize.X_LARGE
        "xx-large" -> FontSize.AbsoluteSize.XX_LARGE
        "xxx-large" -> FontSize.AbsoluteSize.XXX_LARGE
        else -> null
    }

    private fun parseRelativeSize(value: String): FontSize.RelativeSize? = when (value) {
        "larger" -> FontSize.RelativeSize.LARGER
        "smaller" -> FontSize.RelativeSize.SMALLER
        else -> null
    }
}
