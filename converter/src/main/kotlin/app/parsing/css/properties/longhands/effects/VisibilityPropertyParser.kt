package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.irmodels.properties.effects.VisibilityProperty
import app.parsing.css.properties.longhands.PropertyParser

object VisibilityPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val visibility = when (trimmed) {
            "visible" -> VisibilityProperty.Visibility.VISIBLE
            "hidden" -> VisibilityProperty.Visibility.HIDDEN
            "collapse" -> VisibilityProperty.Visibility.COLLAPSE
            else -> return null
        }
        return VisibilityProperty(visibility)
    }
}
