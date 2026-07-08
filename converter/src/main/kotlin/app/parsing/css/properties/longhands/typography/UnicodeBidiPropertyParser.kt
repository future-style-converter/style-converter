package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.UnicodeBidiProperty
import app.parsing.css.properties.longhands.PropertyParser

object UnicodeBidiPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val bidi = when (trimmed) {
            "normal" -> UnicodeBidiProperty.UnicodeBidi.NORMAL
            "embed" -> UnicodeBidiProperty.UnicodeBidi.EMBED
            "isolate" -> UnicodeBidiProperty.UnicodeBidi.ISOLATE
            "bidi-override" -> UnicodeBidiProperty.UnicodeBidi.BIDI_OVERRIDE
            "isolate-override" -> UnicodeBidiProperty.UnicodeBidi.ISOLATE_OVERRIDE
            "plaintext" -> UnicodeBidiProperty.UnicodeBidi.PLAINTEXT
            else -> return null
        }
        return UnicodeBidiProperty(bidi)
    }
}
