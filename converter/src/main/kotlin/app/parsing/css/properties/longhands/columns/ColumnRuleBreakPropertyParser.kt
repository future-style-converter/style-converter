package app.parsing.css.properties.longhands.columns

// IR model + the PropertyParser SAM contract.
import app.irmodels.IRProperty
import app.irmodels.properties.columns.ColumnRuleBreakProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * `column-rule-break` — CSS Gap Decorations Level 1 §5.1.
 *
 * Value domain pinned against Chromium's computed style for the css-gaps
 * WPT corpus: exactly `normal | spanning-item | intersection`, initial
 * `normal`. Anything else returns null so the declaration lands in
 * GenericProperty (`_unmapped: true`) instead of being silently coerced to
 * the initial value.
 */
object ColumnRuleBreakPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        // CSS idents are ASCII case-insensitive.
        val keyword = when (value.trim().lowercase()) {
            "normal" -> ColumnRuleBreakProperty.RuleBreak.NORMAL
            "spanning-item" -> ColumnRuleBreakProperty.RuleBreak.SPANNING_ITEM
            "intersection" -> ColumnRuleBreakProperty.RuleBreak.INTERSECTION
            // No silent fallthrough — unknown idents stay visibly unmapped.
            else -> return null
        }
        return ColumnRuleBreakProperty(keyword)
    }
}
