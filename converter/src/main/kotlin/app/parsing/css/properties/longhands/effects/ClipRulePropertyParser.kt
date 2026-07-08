package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.irmodels.properties.effects.ClipRuleProperty
import app.irmodels.properties.effects.ClipRuleValue
import app.parsing.css.properties.longhands.PropertyParser

object ClipRulePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val clipValue = when (trimmed) {
            "nonzero" -> ClipRuleValue.NONZERO
            "evenodd" -> ClipRuleValue.EVENODD
            else -> return null
        }
        return ClipRuleProperty(clipValue)
    }
}
