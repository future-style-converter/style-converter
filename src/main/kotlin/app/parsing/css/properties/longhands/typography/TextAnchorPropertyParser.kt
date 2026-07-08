package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextAnchorProperty
import app.irmodels.properties.typography.TextAnchorValue
import app.parsing.css.properties.longhands.PropertyParser

object TextAnchorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val anchorValue = when (trimmed) {
            "start" -> TextAnchorValue.START
            "middle" -> TextAnchorValue.MIDDLE
            "end" -> TextAnchorValue.END
            else -> return null
        }

        return TextAnchorProperty(anchorValue)
    }
}
