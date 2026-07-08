package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextSpacingTrimProperty
import app.irmodels.properties.typography.TextSpacingTrimValue
import app.parsing.css.properties.longhands.PropertyParser

object TextSpacingTrimPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "normal" -> TextSpacingTrimValue.NORMAL
            "trim-start" -> TextSpacingTrimValue.TRIM_START
            "space-first" -> TextSpacingTrimValue.SPACE_FIRST
            "trim-end" -> TextSpacingTrimValue.TRIM_END
            "space-all" -> TextSpacingTrimValue.SPACE_ALL
            "trim-all" -> TextSpacingTrimValue.TRIM_ALL
            else -> return null
        }
        return TextSpacingTrimProperty(v)
    }
}
