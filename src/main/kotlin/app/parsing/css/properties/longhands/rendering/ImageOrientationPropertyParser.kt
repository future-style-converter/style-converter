package app.parsing.css.properties.longhands.rendering

import app.irmodels.IRProperty
import app.irmodels.properties.rendering.ImageOrientationProperty
import app.irmodels.properties.rendering.ImageOrientationValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.AngleParser

object ImageOrientationPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        return when {
            trimmed == "none" -> ImageOrientationProperty(ImageOrientationValue.None)
            trimmed == "from-image" -> ImageOrientationProperty(ImageOrientationValue.FromImage)
            trimmed.endsWith("flip") -> {
                val anglePart = trimmed.replace("flip", "").trim()
                if (anglePart.isEmpty()) {
                    ImageOrientationProperty(ImageOrientationValue.Angle(
                        value = app.irmodels.IRAngle.fromDegrees(0.0),
                        flip = true
                    ))
                } else {
                    val angle = AngleParser.parse(anglePart) ?: return null
                    ImageOrientationProperty(ImageOrientationValue.Angle(angle, flip = true))
                }
            }
            else -> {
                val angle = AngleParser.parse(trimmed) ?: return null
                ImageOrientationProperty(ImageOrientationValue.Angle(angle, flip = false))
            }
        }
    }
}
