package app.parsing.css.properties.longhands.columns

// IR model + the PropertyParser SAM contract.
import app.irmodels.IRProperty
import app.irmodels.properties.columns.RowRuleBreakProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * `row-rule-break` — CSS Gap Decorations Level 1 §5.1.
 *
 * Row-axis twin of ColumnRuleBreakPropertyParser; identical three-keyword
 * domain (`normal | spanning-item | intersection`, initial `normal`) and the
 * same "unknown ident → null → GenericProperty" contract.
 */
object RowRuleBreakPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        // CSS idents are ASCII case-insensitive.
        val keyword = when (value.trim().lowercase()) {
            "normal" -> RowRuleBreakProperty.RuleBreak.NORMAL
            "spanning-item" -> RowRuleBreakProperty.RuleBreak.SPANNING_ITEM
            "intersection" -> RowRuleBreakProperty.RuleBreak.INTERSECTION
            // No silent fallthrough — unknown idents stay visibly unmapped.
            else -> return null
        }
        return RowRuleBreakProperty(keyword)
    }
}
