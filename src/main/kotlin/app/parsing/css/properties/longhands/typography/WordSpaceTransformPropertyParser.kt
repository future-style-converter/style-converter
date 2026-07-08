package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.WordSpaceTransformProperty
import app.irmodels.properties.typography.WordSpaceTransformValue
import app.parsing.css.properties.longhands.PropertyParser

object WordSpaceTransformPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val transformValue = when (trimmed) {
            "none" -> WordSpaceTransformValue.NONE
            "auto" -> WordSpaceTransformValue.AUTO
            "space" -> WordSpaceTransformValue.SPACE
            "ideographic-space" -> WordSpaceTransformValue.IDEOGRAPHIC_SPACE
            else -> return null
        }
        return WordSpaceTransformProperty(transformValue)
    }
}
