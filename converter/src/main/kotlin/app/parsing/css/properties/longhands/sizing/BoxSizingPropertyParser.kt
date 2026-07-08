package app.parsing.css.properties.longhands.sizing

import app.irmodels.IRProperty
import app.irmodels.properties.sizing.BoxSizingProperty
import app.parsing.css.properties.longhands.PropertyParser

object BoxSizingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val sizing = when (trimmed) {
            "content-box" -> BoxSizingProperty.BoxSizing.CONTENT_BOX
            "border-box" -> BoxSizingProperty.BoxSizing.BORDER_BOX
            else -> return null
        }
        return BoxSizingProperty(sizing)
    }
}
