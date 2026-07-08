package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.ViewTransitionNameProperty
import app.irmodels.properties.animations.ViewTransitionNameValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parses the CSS `view-transition-name` property.
 *
 * Syntax: none | <custom-ident>
 *
 * Examples:
 * - "none" → None
 * - "header" → Named("header")
 * - "sidebar" → Named("sidebar")
 */
object ViewTransitionNamePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val nameValue = when (trimmed) {
            "none" -> ViewTransitionNameValue.None
            else -> ViewTransitionNameValue.Named(value.trim())
        }

        return ViewTransitionNameProperty(nameValue)
    }
}
