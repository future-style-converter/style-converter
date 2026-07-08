package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.GlyphOrientationHorizontalProperty
import app.irmodels.properties.typography.GlyphOrientationHorizontalValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.AngleParser

object GlyphOrientationHorizontalPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        val angle = AngleParser.parse(trimmed) ?: return null
        val orientationValue = GlyphOrientationHorizontalValue.Angle(angle)

        return GlyphOrientationHorizontalProperty(orientationValue)
    }
}
