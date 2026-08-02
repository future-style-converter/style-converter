package app.parsing.css.properties.shorthands

/**
 * Expands the `column-rule` shorthand property.
 *
 * Column-axis twin of [RowRuleExpander]. Both delegate to the SAME
 * [RuleShorthandExpansion] classifier so a fix on one axis always lands on
 * the other.
 *
 * Syntax: column-rule: <width> || <style> || <color>  (order-independent)
 *
 * Examples:
 * - "1px solid black" → column-rule-width: 1px, column-rule-style: solid, column-rule-color: black
 * - "dotted red"      → column-rule-style: dotted, column-rule-color: red
 * - "medium solid green" → the `<line-width>` KEYWORD lands on
 *   column-rule-width (it used to be swallowed by the colour branch and
 *   rendered as `transparent`).
 */
object ColumnRuleExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> =
        // Only the longhand prefix and the shorthand's own name differ.
        RuleShorthandExpansion.expand(value, prefix = "column-rule", selfName = "column-rule")
}
