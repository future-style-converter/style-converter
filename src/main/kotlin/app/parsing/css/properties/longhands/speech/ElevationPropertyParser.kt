package app.parsing.css.properties.longhands.speech

import app.irmodels.IRProperty
import app.irmodels.properties.speech.ElevationProperty
import app.irmodels.properties.speech.ElevationValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.AngleParser

object ElevationPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Check for named positions
        val namedPositions = listOf("below", "level", "above", "higher", "lower")

        if (trimmed in namedPositions) {
            return ElevationProperty(ElevationValue.Named(trimmed))
        }

        // Try to parse as angle
        val angle = AngleParser.parse(trimmed)
        if (angle != null) {
            return ElevationProperty(ElevationValue.Angle(angle))
        }

        return null
    }
}
