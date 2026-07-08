package app.parsing.css.properties.longhands.interactions

import app.irmodels.IRProperty
import app.irmodels.properties.interactions.InteractivityProperty
import app.irmodels.properties.interactions.InteractivityValue
import app.parsing.css.properties.longhands.PropertyParser

object InteractivityPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase()) {
            "auto" -> InteractivityValue.AUTO
            "inert" -> InteractivityValue.INERT
            else -> return null
        }
        return InteractivityProperty(v)
    }
}
