package app.parsing.css.properties.longhands.rendering

import app.irmodels.IRProperty
import app.irmodels.properties.rendering.InputSecurityProperty
import app.irmodels.properties.rendering.InputSecurityValue
import app.parsing.css.properties.longhands.PropertyParser

object InputSecurityPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "auto" -> InputSecurityValue.AUTO
            "none" -> InputSecurityValue.NONE
            else -> return null
        }
        return InputSecurityProperty(v)
    }
}
