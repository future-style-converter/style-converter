package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.interactions.ScrollBehaviorProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `scroll-behavior` property.
 *
 * Syntax: auto | smooth
 */
object ScrollBehaviorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val behavior = when (trimmed) {
            "auto" -> ScrollBehaviorProperty.ScrollBehavior.AUTO
            "smooth" -> ScrollBehaviorProperty.ScrollBehavior.SMOOTH
            else -> return null
        }

        return ScrollBehaviorProperty(behavior)
    }
}
