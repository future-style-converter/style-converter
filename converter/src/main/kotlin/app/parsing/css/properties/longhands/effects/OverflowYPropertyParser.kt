package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.irmodels.OverflowValue
import app.irmodels.properties.effects.OverflowYProperty
import app.parsing.css.properties.longhands.PropertyParser

object OverflowYPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val overflow = when (trimmed) {
            "visible" -> OverflowValue.VISIBLE
            "hidden" -> OverflowValue.HIDDEN
            "clip" -> OverflowValue.CLIP
            "scroll" -> OverflowValue.SCROLL
            "auto" -> OverflowValue.AUTO
            else -> return null
        }
        return OverflowYProperty(overflow)
    }
}
