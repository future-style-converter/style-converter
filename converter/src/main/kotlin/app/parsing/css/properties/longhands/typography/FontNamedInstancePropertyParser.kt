package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontNamedInstanceProperty
import app.irmodels.properties.typography.FontNamedInstanceValue
import app.parsing.css.properties.longhands.PropertyParser

object FontNamedInstancePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val instanceValue = if (trimmed == "auto") {
            FontNamedInstanceValue.Auto
        } else {
            // Named instance should be a quoted string
            val name = value.trim().removeSurrounding("\"").removeSurrounding("'")
            FontNamedInstanceValue.Named(name)
        }

        return FontNamedInstanceProperty(instanceValue)
    }
}
