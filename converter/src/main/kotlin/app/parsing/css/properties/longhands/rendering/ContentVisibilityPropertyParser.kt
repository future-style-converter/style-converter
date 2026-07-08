package app.parsing.css.properties.longhands.rendering

import app.irmodels.IRProperty
import app.irmodels.properties.rendering.ContentVisibilityProperty
import app.irmodels.properties.rendering.ContentVisibilityValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `content-visibility` property.
 */
object ContentVisibilityPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim().lowercase()

        val contentVisibilityValue = when (normalized) {
            "visible" -> ContentVisibilityValue.VISIBLE
            "auto" -> ContentVisibilityValue.AUTO
            "hidden" -> ContentVisibilityValue.HIDDEN
            else -> return null
        }

        return ContentVisibilityProperty(contentVisibilityValue)
    }
}
