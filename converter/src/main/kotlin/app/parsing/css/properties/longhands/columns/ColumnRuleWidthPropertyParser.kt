package app.parsing.css.properties.longhands.columns

import app.irmodels.IRProperty
import app.irmodels.properties.columns.ColumnRuleWidthProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object ColumnRuleWidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val width = when (trimmed) {
            "thin" -> ColumnRuleWidthProperty.RuleWidth.Keyword(ColumnRuleWidthProperty.RuleWidthKeyword.THIN)
            "medium" -> ColumnRuleWidthProperty.RuleWidth.Keyword(ColumnRuleWidthProperty.RuleWidthKeyword.MEDIUM)
            "thick" -> ColumnRuleWidthProperty.RuleWidth.Keyword(ColumnRuleWidthProperty.RuleWidthKeyword.THICK)
            else -> {
                val length = LengthParser.parse(trimmed) ?: return null
                ColumnRuleWidthProperty.RuleWidth.LengthValue(length)
            }
        }

        return ColumnRuleWidthProperty(width)
    }
}
