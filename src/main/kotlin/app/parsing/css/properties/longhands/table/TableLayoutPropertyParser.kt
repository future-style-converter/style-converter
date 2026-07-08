package app.parsing.css.properties.longhands.table

import app.irmodels.IRProperty
import app.irmodels.properties.table.TableLayoutProperty
import app.parsing.css.properties.longhands.PropertyParser

object TableLayoutPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val layout = when (value.trim().lowercase()) {
            "auto" -> TableLayoutProperty.TableLayout.AUTO
            "fixed" -> TableLayoutProperty.TableLayout.FIXED
            else -> return null
        }
        return TableLayoutProperty(layout)
    }
}
