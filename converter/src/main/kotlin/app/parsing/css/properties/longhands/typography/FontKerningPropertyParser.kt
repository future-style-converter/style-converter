package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontKerning
import app.irmodels.properties.typography.FontKerningProperty
import app.parsing.css.properties.longhands.PropertyParser

object FontKerningPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val kerning = when (trimmed) {
            "auto" -> FontKerning.AUTO
            "normal" -> FontKerning.NORMAL
            "none" -> FontKerning.NONE
            else -> return null
        }
        return FontKerningProperty(kerning)
    }
}
