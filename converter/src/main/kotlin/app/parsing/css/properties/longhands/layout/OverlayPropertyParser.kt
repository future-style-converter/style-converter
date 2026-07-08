package app.parsing.css.properties.longhands.layout

import app.irmodels.IRProperty
import app.irmodels.properties.layout.OverlayProperty
import app.irmodels.properties.layout.OverlayValue
import app.parsing.css.properties.longhands.PropertyParser

object OverlayPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "none" -> OverlayValue.NONE
            "auto" -> OverlayValue.AUTO
            else -> return null
        }
        return OverlayProperty(v)
    }
}
