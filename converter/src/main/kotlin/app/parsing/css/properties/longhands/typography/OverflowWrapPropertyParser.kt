package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.OverflowWrapProperty
import app.parsing.css.properties.longhands.PropertyParser

object OverflowWrapPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val wrap = when (trimmed) {
            "normal" -> OverflowWrapProperty.OverflowWrap.NORMAL
            "break-word" -> OverflowWrapProperty.OverflowWrap.BREAK_WORD
            "anywhere" -> OverflowWrapProperty.OverflowWrap.ANYWHERE
            else -> return null
        }
        return OverflowWrapProperty(wrap)
    }
}
