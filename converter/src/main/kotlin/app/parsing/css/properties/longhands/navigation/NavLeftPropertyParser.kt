package app.parsing.css.properties.longhands.navigation

import app.irmodels.IRProperty
import app.irmodels.properties.navigation.NavLeftProperty
import app.irmodels.properties.navigation.NavLeftValue
import app.parsing.css.properties.longhands.PropertyParser

object NavLeftPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        return when (trimmed.lowercase()) {
            "auto" -> NavLeftProperty(NavLeftValue.Auto)
            else -> {
                val id = if (trimmed.startsWith("#")) trimmed.substring(1) else trimmed
                NavLeftProperty(NavLeftValue.Id(id))
            }
        }
    }
}
