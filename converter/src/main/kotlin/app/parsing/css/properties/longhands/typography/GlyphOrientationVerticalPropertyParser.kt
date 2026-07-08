package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.GlyphOrientationVerticalProperty
import app.irmodels.properties.typography.GlyphOrientationVerticalValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.AngleParser

object GlyphOrientationVerticalPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val orientationValue = when {
            trimmed == "auto" -> GlyphOrientationVerticalValue.Auto
            else -> {
                val angle = AngleParser.parse(trimmed) ?: return null
                GlyphOrientationVerticalValue.Angle(angle)
            }
        }

        return GlyphOrientationVerticalProperty(orientationValue)
    }
}
