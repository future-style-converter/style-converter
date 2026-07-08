package app.parsing.css.properties.longhands.typography

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextSizeAdjustProperty
import app.irmodels.properties.typography.TextSizeAdjustValue
import app.parsing.css.properties.longhands.PropertyParser

object TextSizeAdjustPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val adjustValue = when (trimmed) {
            "none" -> TextSizeAdjustValue.None
            "auto" -> TextSizeAdjustValue.Auto
            else -> {
                if (trimmed.endsWith("%")) {
                    val num = trimmed.removeSuffix("%").toDoubleOrNull() ?: return null
                    TextSizeAdjustValue.Percentage(IRPercentage(num))
                } else {
                    return null
                }
            }
        }

        return TextSizeAdjustProperty(adjustValue)
    }
}
