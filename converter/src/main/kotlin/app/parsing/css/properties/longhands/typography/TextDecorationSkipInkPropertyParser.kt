package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextDecorationSkipInkProperty
import app.irmodels.properties.typography.TextDecorationSkipInkValue
import app.parsing.css.properties.longhands.PropertyParser

object TextDecorationSkipInkPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val skipInkValue = when (trimmed) {
            "none" -> TextDecorationSkipInkValue.NONE
            "auto" -> TextDecorationSkipInkValue.AUTO
            "all" -> TextDecorationSkipInkValue.ALL
            else -> return null
        }

        return TextDecorationSkipInkProperty(skipInkValue)
    }
}
