package com.styleconverter.runtime.layout.position

import com.styleconverter.runtime.core.types.ValueExtractors
import kotlinx.serialization.json.JsonElement

/**
 * Extracts position configuration from IR properties.
 *
 * Handles the following CSS properties:
 * - position: static | relative | absolute | fixed | sticky
 * - top, right, bottom, left
 * - inset-block-start, inset-block-end (logical properties)
 * - inset-inline-start, inset-inline-end (logical properties)
 * - z-index
 */
object PositionExtractor {

    /**
     * Extract a complete PositionConfig from a list of property type/data pairs.
     *
     * @param properties List of (propertyType, data) pairs from IR properties
     * @return PositionConfig with all extracted position values
     */
    fun extractPositionConfig(properties: List<Pair<String, JsonElement?>>): PositionConfig {
        var config = PositionConfig()
        // Wave 49 (lane A7) — percentage insets are collected ALONGSIDE the
        // Dp slots, never instead of them: the Dp read below stays exactly
        // what it was (the bare percentage number taken as pixels), so any
        // consumer that cannot see a containing block keeps its frozen
        // geometry. PositionApplier upgrades these where the channel is
        // available. See PercentInsetResolve for the CSS citation.
        var pct = PercentInsets()

        properties.forEach { (type, data) ->
            // Record the percentage magnitude first — `percentOf` answers
            // null for every non-percentage wire shape, so this is a no-op
            // for the `{"px":N}` insets that make up the whole corpus bar
            // seven tests.
            PercentInsetResolve.percentOf(data)?.let { p ->
                pct = when (type) {
                    "Top" -> pct.copy(top = p)
                    "Right" -> pct.copy(right = p)
                    "Bottom" -> pct.copy(bottom = p)
                    "Left" -> pct.copy(left = p)
                    "InsetBlockStart" -> pct.copy(insetBlockStart = p)
                    "InsetBlockEnd" -> pct.copy(insetBlockEnd = p)
                    "InsetInlineStart" -> pct.copy(insetInlineStart = p)
                    "InsetInlineEnd" -> pct.copy(insetInlineEnd = p)
                    // ZIndex is also a bare number on the wire but is not an
                    // inset — it must never enter the percentage channel.
                    else -> pct
                }
            }
            config = when (type) {
                // Position type
                "Position" -> config.copy(type = parsePositionType(ValueExtractors.extractKeyword(data)))

                // Physical offset properties
                "Top" -> config.copy(top = ValueExtractors.extractDp(data))
                "Right" -> config.copy(end = ValueExtractors.extractDp(data))
                "Bottom" -> config.copy(bottom = ValueExtractors.extractDp(data))
                "Left" -> config.copy(start = ValueExtractors.extractDp(data))

                // Logical offset properties (writing mode aware)
                "InsetBlockStart" -> config.copy(insetBlockStart = ValueExtractors.extractDp(data))
                "InsetBlockEnd" -> config.copy(insetBlockEnd = ValueExtractors.extractDp(data))
                "InsetInlineStart" -> config.copy(insetInlineStart = ValueExtractors.extractDp(data))
                "InsetInlineEnd" -> config.copy(insetInlineEnd = ValueExtractors.extractDp(data))

                // Z-index for stacking
                "ZIndex" -> config.copy(zIndex = ValueExtractors.extractFloat(data) ?: 0f)

                else -> config
            }
        }

        // Attach the percentage side-channel only when something was
        // actually declared as a percentage: a null keeps PositionConfig's
        // equality (and therefore every existing unit test's expected
        // config) unchanged for the px-only corpus.
        return if (pct.any) config.copy(percent = pct) else config
    }

    /**
     * Parse position type keyword to enum.
     *
     * @param keyword The CSS position keyword (static, relative, absolute, fixed, sticky)
     * @return PositionType enum value, defaults to STATIC for unknown values
     */
    private fun parsePositionType(keyword: String?): PositionType {
        return when (keyword?.lowercase()) {
            "relative" -> PositionType.RELATIVE
            "absolute" -> PositionType.ABSOLUTE
            "fixed" -> PositionType.FIXED
            "sticky" -> PositionType.STICKY
            "static" -> PositionType.STATIC
            else -> PositionType.STATIC
        }
    }

    /**
     * Check if a property type is a position-related property.
     *
     * @param propertyType The IR property type string
     * @return True if this is a position property
     */
    fun isPositionProperty(propertyType: String): Boolean {
        return propertyType in POSITION_PROPERTIES
    }

    private val POSITION_PROPERTIES = setOf(
        "Position",
        "Top", "Right", "Bottom", "Left",
        "InsetBlockStart", "InsetBlockEnd",
        "InsetInlineStart", "InsetInlineEnd",
        "ZIndex"
    )
}
