package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.irmodels.OverflowValue
import app.irmodels.properties.effects.OverflowInlineProperty
import app.parsing.css.properties.longhands.PropertyParser

object OverflowInlinePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val overflowValue = when (trimmed) {
            "visible" -> OverflowValue.VISIBLE
            "hidden" -> OverflowValue.HIDDEN
            "clip" -> OverflowValue.CLIP
            "scroll" -> OverflowValue.SCROLL
            "auto" -> OverflowValue.AUTO
            else -> return null
        }
        return OverflowInlineProperty(overflowValue)
    }
}
