package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.PositionVisibilityProperty
import app.irmodels.properties.layout.advanced.PositionVisibilityValue
import app.parsing.css.properties.longhands.PropertyParser

object PositionVisibilityPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "always" -> PositionVisibilityValue.ALWAYS
            "anchors-visible" -> PositionVisibilityValue.ANCHORS_VISIBLE
            "no-overflow" -> PositionVisibilityValue.NO_OVERFLOW
            else -> return null
        }
        return PositionVisibilityProperty(v)
    }
}
