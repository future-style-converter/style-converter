package app.parsing.css.properties.longhands.transforms

import app.irmodels.IRProperty
import app.irmodels.properties.transforms.BackfaceVisibilityProperty
import app.parsing.css.properties.longhands.PropertyParser

object BackfaceVisibilityPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val visibility = when (trimmed) {
            "visible" -> BackfaceVisibilityProperty.BackfaceVisibility.VISIBLE
            "hidden" -> BackfaceVisibilityProperty.BackfaceVisibility.HIDDEN
            else -> return null
        }
        return BackfaceVisibilityProperty(visibility)
    }
}
