package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextSpacingProperty
import app.irmodels.properties.typography.TextSpacingProperty.TextSpacingValue
import app.parsing.css.properties.longhands.PropertyParser

object TextSpacingPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val v = when (value.trim().lowercase()) {
            "normal" -> TextSpacingValue.Normal
            "none" -> TextSpacingValue.None
            "auto" -> TextSpacingValue.Auto
            else -> TextSpacingValue.Raw(value.trim())
        }
        return TextSpacingProperty(v)
    }
}
