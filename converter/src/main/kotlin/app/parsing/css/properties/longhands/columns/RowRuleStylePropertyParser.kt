package app.parsing.css.properties.longhands.columns

// The IR model this parser produces plus the PropertyParser SAM contract.
import app.irmodels.IRProperty
import app.irmodels.properties.columns.RowRuleStyleProperty
import app.parsing.css.properties.longhands.PropertyParser

/**
 * `row-rule-style` — CSS Gap Decorations Level 1 §4.2.
 *
 * Byte-for-byte mirror of ColumnRuleStylePropertyParser (same file, same
 * ten `<line-style>` idents, same "unknown ident → null" behaviour so the
 * declaration falls through to GenericProperty and stays visibly unmapped
 * rather than being silently coerced).
 */
object RowRuleStylePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        // CSS idents are ASCII case-insensitive; normalise before matching.
        val style = when (value.trim().lowercase()) {
            "none" -> RowRuleStyleProperty.RuleStyle.NONE
            "hidden" -> RowRuleStyleProperty.RuleStyle.HIDDEN
            "dotted" -> RowRuleStyleProperty.RuleStyle.DOTTED
            "dashed" -> RowRuleStyleProperty.RuleStyle.DASHED
            "solid" -> RowRuleStyleProperty.RuleStyle.SOLID
            "double" -> RowRuleStyleProperty.RuleStyle.DOUBLE
            "groove" -> RowRuleStyleProperty.RuleStyle.GROOVE
            "ridge" -> RowRuleStyleProperty.RuleStyle.RIDGE
            "inset" -> RowRuleStyleProperty.RuleStyle.INSET
            "outset" -> RowRuleStyleProperty.RuleStyle.OUTSET
            // No silent fallthrough: unrecognised value → null → Generic.
            else -> return null
        }
        return RowRuleStyleProperty(style)
    }
}
