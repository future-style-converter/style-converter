package app.parsing.css.properties.longhands.color

import app.irmodels.IRProperty
import app.irmodels.properties.color.DynamicRangeLimitProperty
import app.irmodels.properties.color.DynamicRangeLimitValue
import app.parsing.css.properties.longhands.PropertyParser

object DynamicRangeLimitPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase().replace("-", "_")) {
            "standard" -> DynamicRangeLimitValue.STANDARD
            "high" -> DynamicRangeLimitValue.HIGH
            "constrained_high" -> DynamicRangeLimitValue.CONSTRAINED_HIGH
            else -> return null
        }
        return DynamicRangeLimitProperty(v)
    }
}
