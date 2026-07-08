package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollSnapTypeProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `scroll-snap-type` property.
 *
 * Syntax: none | [ x | y | block | inline | both ] [ mandatory | proximity ]?
 */
object ScrollSnapTypePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle 'none' keyword
        if (trimmed == "none") {
            return ScrollSnapTypeProperty(snapType = null)
        }

        // Split into parts
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.isEmpty()) return null

        // Parse axis (required)
        val axis = when (parts[0]) {
            "x" -> ScrollSnapTypeProperty.Axis.X
            "y" -> ScrollSnapTypeProperty.Axis.Y
            "block" -> ScrollSnapTypeProperty.Axis.BLOCK
            "inline" -> ScrollSnapTypeProperty.Axis.INLINE
            "both" -> ScrollSnapTypeProperty.Axis.BOTH
            else -> return null
        }

        // Parse strictness (optional, defaults to proximity)
        val strictness = if (parts.size > 1) {
            when (parts[1]) {
                "mandatory" -> ScrollSnapTypeProperty.Strictness.MANDATORY
                "proximity" -> ScrollSnapTypeProperty.Strictness.PROXIMITY
                else -> return null
            }
        } else {
            ScrollSnapTypeProperty.Strictness.PROXIMITY
        }

        return ScrollSnapTypeProperty(
            snapType = ScrollSnapTypeProperty.SnapType(axis, strictness)
        )
    }
}
