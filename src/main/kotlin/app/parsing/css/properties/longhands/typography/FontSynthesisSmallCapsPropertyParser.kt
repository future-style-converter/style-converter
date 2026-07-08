package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontSynthesisSmallCapsProperty
import app.irmodels.properties.typography.FontSynthesisSmallCapsValue
import app.parsing.css.properties.longhands.PropertyParser

object FontSynthesisSmallCapsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val synthesisValue = when (trimmed) {
            "auto" -> FontSynthesisSmallCapsValue.AUTO
            "none" -> FontSynthesisSmallCapsValue.NONE
            else -> return null
        }

        return FontSynthesisSmallCapsProperty(synthesisValue)
    }
}
