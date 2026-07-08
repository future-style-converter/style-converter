package app.parsing.css.properties.longhands.svg

import app.irmodels.IRProperty
import app.irmodels.properties.svg.FillRule
import app.irmodels.properties.svg.FillRuleProperty
import app.parsing.css.properties.longhands.PropertyParser

object FillRulePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val normalized = value.trim().lowercase()
        val rule = when (normalized) {
            "nonzero" -> FillRule.NONZERO
            "evenodd" -> FillRule.EVENODD
            else -> return null
        }
        return FillRuleProperty(rule)
    }
}
