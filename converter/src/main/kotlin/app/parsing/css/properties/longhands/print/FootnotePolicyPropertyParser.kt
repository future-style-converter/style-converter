package app.parsing.css.properties.longhands.print

import app.irmodels.IRProperty
import app.irmodels.properties.print.FootnotePolicyProperty
import app.irmodels.properties.print.FootnotePolicyValue
import app.parsing.css.properties.longhands.PropertyParser

object FootnotePolicyPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val policyValue = when (trimmed) {
            "auto" -> FootnotePolicyValue.AUTO
            "line" -> FootnotePolicyValue.LINE
            "block" -> FootnotePolicyValue.BLOCK
            else -> return null
        }
        return FootnotePolicyProperty(policyValue)
    }
}
