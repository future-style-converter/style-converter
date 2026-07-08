package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.irmodels.IRUrl
import app.irmodels.properties.effects.MaskBorderSourceProperty
import app.parsing.css.properties.longhands.PropertyParser

object MaskBorderSourcePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        if (trimmed.lowercase() == "none") {
            return MaskBorderSourceProperty(IRUrl(""))
        }
        val url = if (trimmed.startsWith("url(") && trimmed.endsWith(")")) {
            trimmed.removePrefix("url(").removeSuffix(")").trim()
                .removeSurrounding("\"").removeSurrounding("'")
        } else {
            trimmed
        }
        return MaskBorderSourceProperty(IRUrl(url))
    }
}
