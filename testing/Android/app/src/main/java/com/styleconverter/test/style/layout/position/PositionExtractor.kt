package com.styleconverter.test.style.layout.position

import com.styleconverter.test.style.core.types.ValueExtractors
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

        properties.forEach { (type, data) ->
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

        return config
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
