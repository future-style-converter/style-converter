package app.parsing.css.properties.longhands.table

import app.irmodels.IRProperty
import app.irmodels.properties.table.BorderCollapseProperty
import app.parsing.css.properties.longhands.PropertyParser

object BorderCollapsePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val collapse = when (value.trim().lowercase()) {
            "collapse" -> BorderCollapseProperty.BorderCollapse.COLLAPSE
            "separate" -> BorderCollapseProperty.BorderCollapse.SEPARATE
            else -> return null
        }
        return BorderCollapseProperty(collapse)
    }
}
