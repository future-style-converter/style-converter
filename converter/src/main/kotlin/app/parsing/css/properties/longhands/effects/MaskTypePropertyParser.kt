package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.irmodels.properties.effects.MaskTypeProperty
import app.irmodels.properties.effects.MaskTypeValue
import app.parsing.css.properties.longhands.PropertyParser

object MaskTypePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val maskValue = when (trimmed) {
            "luminance" -> MaskTypeValue.LUMINANCE
            "alpha" -> MaskTypeValue.ALPHA
            else -> return null
        }
        return MaskTypeProperty(maskValue)
    }
}
