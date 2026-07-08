package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.MarkerMidProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.UrlParser

object MarkerMidPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim().lowercase()

        // Handle "none" keyword
        if (normalized == "none") {
            return null
        }

        val url = UrlParser.parse(value) ?: return null
        return MarkerMidProperty(url)
    }
}
