package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.OverscrollBehaviorProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `overscroll-behavior` property.
 *
 * Syntax: [ auto | contain | none ]{1,2}
 */
object OverscrollBehaviorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Split into parts
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.isEmpty() || parts.size > 2) return null

        // Parse x behavior (required)
        val x = parseBehavior(parts[0]) ?: return null

        // Parse y behavior (optional, defaults to x value)
        val y = if (parts.size > 1) {
            parseBehavior(parts[1])
        } else {
            null
        }

        return OverscrollBehaviorProperty(x, y)
    }

    private fun parseBehavior(value: String): OverscrollBehaviorProperty.Behavior? {
        return when (value) {
            "auto" -> OverscrollBehaviorProperty.Behavior.AUTO
            "contain" -> OverscrollBehaviorProperty.Behavior.CONTAIN
            "none" -> OverscrollBehaviorProperty.Behavior.NONE
            else -> null
        }
    }
}
