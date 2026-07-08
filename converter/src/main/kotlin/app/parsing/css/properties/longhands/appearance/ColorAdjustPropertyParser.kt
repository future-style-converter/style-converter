package app.parsing.css.properties.longhands.appearance

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.properties.appearance.*
/**
 * Parser for `color-adjust` property.
 *
 * Syntax: economy | exact
 */
object ColorAdjustPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val colorAdjust = when (trimmed) {
            "economy" -> ColorAdjustValue.ECONOMY
            "exact" -> ColorAdjustValue.EXACT
            else -> return null
        }
        return ColorAdjustProperty(colorAdjust)
    }
}
