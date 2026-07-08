package app.parsing.css.properties.longhands.navigation

import app.irmodels.IRProperty
import app.irmodels.properties.navigation.NavRightProperty
import app.irmodels.properties.navigation.NavRightValue
import app.parsing.css.properties.longhands.PropertyParser

object NavRightPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        return when (trimmed.lowercase()) {
            "auto" -> NavRightProperty(NavRightValue.Auto)
            else -> {
                val id = if (trimmed.startsWith("#")) trimmed.substring(1) else trimmed
                NavRightProperty(NavRightValue.Id(id))
            }
        }
    }
}
