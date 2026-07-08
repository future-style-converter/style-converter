package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.HyphenateLimitLastProperty
import app.irmodels.properties.typography.HyphenateLimitLastValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `hyphenate-limit-last` property.
 *
 * Values: none | always | column | page | spread
 * Note: IR model uses LAST instead of 'always' from CSS spec
 */
object HyphenateLimitLastPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val limit = when (trimmed) {
            "none" -> HyphenateLimitLastValue.NONE
            "always", "last" -> HyphenateLimitLastValue.LAST
            "column" -> HyphenateLimitLastValue.COLUMN
            "page" -> HyphenateLimitLastValue.PAGE
            "spread" -> HyphenateLimitLastValue.SPREAD
            else -> return null
        }

        return HyphenateLimitLastProperty(limit)
    }
}
