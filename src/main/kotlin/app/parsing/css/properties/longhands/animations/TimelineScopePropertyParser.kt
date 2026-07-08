package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.TimelineScopeProperty
import app.irmodels.properties.animations.TimelineScopeValue
import app.parsing.css.properties.longhands.PropertyParser

object TimelineScopePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when {
            trimmed == "none" -> TimelineScopeValue.None
            trimmed == "all" -> TimelineScopeValue.All
            else -> {
                val names = value.trim().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (names.isEmpty()) return null
                TimelineScopeValue.Names(names)
            }
        }
        return TimelineScopeProperty(v)
    }
}
