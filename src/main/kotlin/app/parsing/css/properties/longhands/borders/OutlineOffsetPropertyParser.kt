package app.parsing.css.properties.longhands.borders

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.IRPercentage
import app.irmodels.properties.borders.OutlineOffsetProperty
import app.irmodels.properties.layout.position.*
import app.parsing.css.properties.primitiveParsers.LengthParser
/**
 * Parser for `outline-offset` property.
 *
 * Syntax: <length>
 */
object OutlineOffsetPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val length = LengthParser.parse(value.trim()) ?: return null
        return OutlineOffsetProperty(length)
    }
}
