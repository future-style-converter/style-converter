package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.irmodels.MaskBorderModeValue
import app.irmodels.properties.effects.MaskBorderModeProperty
import app.parsing.css.properties.longhands.PropertyParser

object MaskBorderModePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val modeValue = when (trimmed) {
            "luminance" -> MaskBorderModeValue.LUMINANCE
            "alpha" -> MaskBorderModeValue.ALPHA
            else -> return null
        }
        return MaskBorderModeProperty(modeValue)
    }
}
