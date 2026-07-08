package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.layout.flexbox.FlexWrapProperty
import app.parsing.css.properties.longhands.PropertyParser

object FlexWrapPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val wrap = when (trimmed) {
            "nowrap" -> FlexWrapProperty.FlexWrap.NOWRAP
            "wrap" -> FlexWrapProperty.FlexWrap.WRAP
            "wrap-reverse" -> FlexWrapProperty.FlexWrap.WRAP_REVERSE
            else -> return null
        }
        return FlexWrapProperty(wrap)
    }
}
