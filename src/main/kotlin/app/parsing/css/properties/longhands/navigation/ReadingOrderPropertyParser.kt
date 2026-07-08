package app.parsing.css.properties.longhands.navigation

import app.irmodels.IRProperty
import app.irmodels.properties.navigation.ReadingOrderProperty
import app.irmodels.properties.navigation.ReadingOrderValue
import app.parsing.css.properties.longhands.PropertyParser

object ReadingOrderPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase().replace("-", "_")) {
            "normal" -> ReadingOrderValue.NORMAL
            "source_order" -> ReadingOrderValue.SOURCE_ORDER
            else -> return null
        }
        return ReadingOrderProperty(v)
    }
}
