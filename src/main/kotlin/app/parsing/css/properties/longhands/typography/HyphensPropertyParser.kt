package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.HyphensProperty
import app.parsing.css.properties.longhands.PropertyParser

object HyphensPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val hyphens = when (trimmed) {
            "none" -> HyphensProperty.Hyphens.NONE
            "manual" -> HyphensProperty.Hyphens.MANUAL
            "auto" -> HyphensProperty.Hyphens.AUTO
            else -> return null
        }
        return HyphensProperty(hyphens)
    }
}
