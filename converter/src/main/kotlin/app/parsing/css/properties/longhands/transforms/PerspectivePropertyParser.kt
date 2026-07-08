package app.parsing.css.properties.longhands.transforms

import app.irmodels.IRProperty
import app.irmodels.properties.transforms.PerspectiveProperty
import app.irmodels.properties.transforms.PerspectiveValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

/**
 * Parser for the CSS `perspective` property.
 *
 * Syntax: none | <length>
 *
 * Examples:
 * - "none" → PerspectiveValue.None
 * - "1000px" → PerspectiveValue.Length(IRLength(1000.0, PX))
 * - "50em" → PerspectiveValue.Length(IRLength(50.0, EM))
 */
object PerspectivePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle 'none' keyword
        if (trimmed == "none") {
            return PerspectiveProperty(PerspectiveValue.None)
        }

        // Parse as length
        val length = LengthParser.parse(trimmed) ?: return null
        return PerspectiveProperty(PerspectiveValue.Length(length))
    }
}
