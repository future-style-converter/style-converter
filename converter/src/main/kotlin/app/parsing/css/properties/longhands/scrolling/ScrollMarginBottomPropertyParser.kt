package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.ScrollPaddingValue
import app.irmodels.properties.scrolling.*
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

/**
 * Parser for `scroll-margin-bottom` property.
 *
 * Syntax: <length>
 */
object ScrollMarginBottomPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val length = LengthParser.parse(trimmed) ?: return null
        return ScrollMarginBottomProperty(length)
    }
}
