package app.parsing.css.properties.longhands.performance

import app.irmodels.IRProperty
import app.irmodels.properties.performance.IsolationProperty
import app.parsing.css.properties.longhands.PropertyParser

object IsolationPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val isolation = when (value.trim().lowercase()) {
            "auto" -> IsolationProperty.Isolation.AUTO
            "isolate" -> IsolationProperty.Isolation.ISOLATE
            else -> return null
        }
        return IsolationProperty(isolation)
    }
}
