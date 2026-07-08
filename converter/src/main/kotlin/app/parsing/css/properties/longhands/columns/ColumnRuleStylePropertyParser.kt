package app.parsing.css.properties.longhands.columns

import app.irmodels.IRProperty
import app.irmodels.properties.columns.ColumnRuleStyleProperty
import app.parsing.css.properties.longhands.PropertyParser

object ColumnRuleStylePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val style = when (value.trim().lowercase()) {
            "none" -> ColumnRuleStyleProperty.RuleStyle.NONE
            "hidden" -> ColumnRuleStyleProperty.RuleStyle.HIDDEN
            "dotted" -> ColumnRuleStyleProperty.RuleStyle.DOTTED
            "dashed" -> ColumnRuleStyleProperty.RuleStyle.DASHED
            "solid" -> ColumnRuleStyleProperty.RuleStyle.SOLID
            "double" -> ColumnRuleStyleProperty.RuleStyle.DOUBLE
            "groove" -> ColumnRuleStyleProperty.RuleStyle.GROOVE
            "ridge" -> ColumnRuleStyleProperty.RuleStyle.RIDGE
            "inset" -> ColumnRuleStyleProperty.RuleStyle.INSET
            "outset" -> ColumnRuleStyleProperty.RuleStyle.OUTSET
            else -> return null
        }
        return ColumnRuleStyleProperty(style)
    }
}
