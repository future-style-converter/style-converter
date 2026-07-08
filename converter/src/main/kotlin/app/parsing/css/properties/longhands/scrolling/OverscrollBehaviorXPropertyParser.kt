package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.OverscrollBehavior
import app.irmodels.properties.scrolling.OverscrollBehaviorXProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `overscroll-behavior-x` property.
 *
 * Syntax: auto | contain | none
 */
object OverscrollBehaviorXPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val behavior = when (trimmed) {
            "auto" -> OverscrollBehavior.AUTO
            "contain" -> OverscrollBehavior.CONTAIN
            "none" -> OverscrollBehavior.NONE
            else -> return null
        }

        return OverscrollBehaviorXProperty(behavior)
    }
}
