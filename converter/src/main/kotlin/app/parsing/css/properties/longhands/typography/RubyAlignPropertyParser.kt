package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.RubyAlignProperty
import app.irmodels.properties.typography.RubyAlignValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `ruby-align` property.
 *
 * Values: start | center | space-between | space-around
 */
object RubyAlignPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val alignment = when (trimmed) {
            "start" -> RubyAlignValue.START
            "center" -> RubyAlignValue.CENTER
            "space-between" -> RubyAlignValue.SPACE_BETWEEN
            "space-around" -> RubyAlignValue.SPACE_AROUND
            else -> return null
        }

        return RubyAlignProperty(alignment)
    }
}
