package app.parsing.css.properties.longhands.navigation

import app.irmodels.IRProperty
import app.irmodels.properties.navigation.NavUpProperty
import app.irmodels.properties.navigation.NavUpValue
import app.parsing.css.properties.longhands.PropertyParser

object NavUpPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        return when (trimmed.lowercase()) {
            "auto" -> NavUpProperty(NavUpValue.Auto)
            else -> {
                // Parse as ID (strip # if present)
                val id = if (trimmed.startsWith("#")) trimmed.substring(1) else trimmed
                NavUpProperty(NavUpValue.Id(id))
            }
        }
    }
}
