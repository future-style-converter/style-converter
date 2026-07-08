package app.parsing.css.properties.longhands.animations

import app.irmodels.AnimationRangeValue
import app.irmodels.IRProperty
import app.irmodels.TimelineRangeName
import app.irmodels.properties.animations.AnimationRangeStartProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

/**
 * Parses the CSS `animation-range-start` property.
 *
 * Syntax: <length> | <percentage> | normal | <timeline-range-name> [<percentage>]
 *
 * Examples:
 * - "100px" → Length
 * - "50%" → Percentage
 * - "normal" → Keyword
 * - "cover 25%" → NamedRange(COVER, 25%)
 */
object AnimationRangeStartPropertyParser : PropertyParser {

    private val rangeNames = mapOf(
        "cover" to TimelineRangeName.COVER,
        "contain" to TimelineRangeName.CONTAIN,
        "entry" to TimelineRangeName.ENTRY,
        "exit" to TimelineRangeName.EXIT,
        "entry-crossing" to TimelineRangeName.ENTRY_CROSSING,
        "exit-crossing" to TimelineRangeName.EXIT_CROSSING
    )

    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Try parsing as percentage first
        PercentageParser.parse(trimmed)?.let {
            return AnimationRangeStartProperty(AnimationRangeValue.Percentage(it))
        }

        // Try parsing as length
        LengthParser.parse(trimmed)?.let {
            return AnimationRangeStartProperty(AnimationRangeValue.Length(it))
        }

        // Check for named range with offset (e.g., "cover 25%")
        if (lower.contains(" ")) {
            val parts = lower.split("\\s+".toRegex())
            if (parts.size == 2) {
                val rangeName = rangeNames[parts[0]]
                val offset = PercentageParser.parse(parts[1])
                if (rangeName != null && offset != null) {
                    return AnimationRangeStartProperty(AnimationRangeValue.NamedRange(rangeName, offset))
                }
            }
        }

        // Parse as keyword (normal, or single range name)
        return AnimationRangeStartProperty(AnimationRangeValue.Keyword(lower))
    }
}
