package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextOrientationProperty
import app.parsing.css.properties.longhands.PropertyParser

object TextOrientationPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val orientation = when (trimmed) {
            "mixed" -> TextOrientationProperty.TextOrientation.MIXED
            "upright" -> TextOrientationProperty.TextOrientation.UPRIGHT
            "sideways" -> TextOrientationProperty.TextOrientation.SIDEWAYS
            else -> return null
        }
        return TextOrientationProperty(orientation)
    }
}
