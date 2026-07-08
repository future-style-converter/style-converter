package app.parsing.css.properties.longhands.columns

import app.irmodels.IRProperty
import app.irmodels.properties.columns.ColumnFill
import app.irmodels.properties.columns.ColumnFillProperty
import app.parsing.css.properties.longhands.PropertyParser

object ColumnFillPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val fill = when (value.trim().lowercase()) {
            "auto" -> ColumnFill.AUTO
            "balance" -> ColumnFill.BALANCE
            "balance-all" -> ColumnFill.BALANCE_ALL
            else -> return null
        }
        return ColumnFillProperty(fill)
    }
}
