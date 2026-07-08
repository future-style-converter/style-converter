package app.parsing.css.properties.longhands.navigation

import app.irmodels.IRProperty
import app.irmodels.properties.navigation.NavDownProperty
import app.irmodels.properties.navigation.NavDownValue
import app.parsing.css.properties.longhands.PropertyParser

object NavDownPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        return when (trimmed.lowercase()) {
            "auto" -> NavDownProperty(NavDownValue.Auto)
            else -> {
                val id = if (trimmed.startsWith("#")) trimmed.substring(1) else trimmed
                NavDownProperty(NavDownValue.Id(id))
            }
        }
    }
}
