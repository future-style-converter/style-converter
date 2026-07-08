package app.parsing.css.properties.longhands.effects

import app.irmodels.properties.effects.MaskSizeProperty
import app.irmodels.properties.effects.MaskSizeValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for the `mask-size` CSS property.
 *
 * Accepts:
 * - auto
 * - cover
 * - contain
 * - <length> (e.g., 100px)
 * - <percentage> (e.g., 50%)
 * - <width> <height> (e.g., 100px 50%, auto 100px)
 */
object MaskSizePropertyParser : PropertyParser {
    override fun parse(value: String): MaskSizeProperty? {
        // Strip trailing commas from values like "cover," that come from comma-separated lists
        val trimmed = value.trim().trimEnd(',').trim()
        val lowered = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lowered)) {
            return MaskSizeProperty(
                width = MaskSizeValue.Keyword(lowered),
                height = MaskSizeValue.Keyword(lowered)
            )
        }

        // Check for var() or other complex expressions
        if (ExpressionDetector.containsExpression(lowered)) {
            return MaskSizeProperty(
                width = MaskSizeValue.Raw(trimmed),
                height = MaskSizeValue.Raw(trimmed)
            )
        }

        // Single keyword values
        when (lowered) {
            "cover" -> return MaskSizeProperty(
                width = MaskSizeValue.Cover,
                height = MaskSizeValue.Cover
            )
            "contain" -> return MaskSizeProperty(
                width = MaskSizeValue.Contain,
                height = MaskSizeValue.Contain
            )
            "auto" -> return MaskSizeProperty(
                width = MaskSizeValue.Auto,
                height = MaskSizeValue.Auto
            )
        }

        // Split into width and height
        val parts = lowered.split("""\s+""".toRegex())

        val width = parseSizeValue(parts[0]) ?: MaskSizeValue.Raw(parts[0])
        val height = if (parts.size > 1) {
            parseSizeValue(parts[1]) ?: MaskSizeValue.Raw(parts[1])
        } else {
            width // Single value applies to both width and height
        }

        return MaskSizeProperty(width, height)
    }

    private fun parseSizeValue(value: String): MaskSizeValue? {
        // Strip any trailing commas from individual values
        val cleaned = value.trimEnd(',').trim().lowercase()
        return when (cleaned) {
            "auto" -> MaskSizeValue.Auto
            "cover" -> MaskSizeValue.Cover
            "contain" -> MaskSizeValue.Contain
            else -> {
                // Try length
                LengthParser.parse(cleaned)?.let { return MaskSizeValue.Length(it) }
                // Try percentage
                PercentageParser.parse(cleaned)?.let { return MaskSizeValue.Percentage(it) }
                null
            }
        }
    }
}
