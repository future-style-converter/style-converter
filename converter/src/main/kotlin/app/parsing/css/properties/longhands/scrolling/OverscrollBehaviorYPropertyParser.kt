package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.OverscrollBehavior
import app.irmodels.properties.scrolling.OverscrollBehaviorYProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `overscroll-behavior-y` property.
 *
 * Syntax: auto | contain | none
 */
object OverscrollBehaviorYPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val behavior = when (trimmed) {
            "auto" -> OverscrollBehavior.AUTO
            "contain" -> OverscrollBehavior.CONTAIN
            "none" -> OverscrollBehavior.NONE
            else -> return null
        }

        return OverscrollBehaviorYProperty(behavior)
    }
}
