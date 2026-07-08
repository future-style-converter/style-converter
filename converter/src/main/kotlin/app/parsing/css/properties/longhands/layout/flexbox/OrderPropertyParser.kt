package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.layout.flexbox.OrderProperty
import app.parsing.css.properties.longhands.PropertyParser

object OrderPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val order = value.trim().toIntOrNull() ?: return null
        return OrderProperty(order)
    }
}
