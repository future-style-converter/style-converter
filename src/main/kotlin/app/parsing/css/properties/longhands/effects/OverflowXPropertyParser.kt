package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.irmodels.OverflowValue
import app.irmodels.properties.effects.OverflowXProperty
import app.parsing.css.properties.longhands.PropertyParser

object OverflowXPropertyParser : PropertyParser {
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
        return OverflowXProperty(overflow)
    }
}
