package app.parsing.css.properties.longhands.layout.position

import app.irmodels.IRProperty
import app.irmodels.properties.layout.ClearProperty
import app.parsing.css.properties.longhands.PropertyParser

object ClearPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val clear = when (trimmed) {
            "none" -> ClearProperty.Clear.NONE
            "left" -> ClearProperty.Clear.LEFT
            "right" -> ClearProperty.Clear.RIGHT
            "both" -> ClearProperty.Clear.BOTH
            "inline-start" -> ClearProperty.Clear.INLINE_START
            "inline-end" -> ClearProperty.Clear.INLINE_END
            else -> return null
        }
        return ClearProperty(clear)
    }
}
