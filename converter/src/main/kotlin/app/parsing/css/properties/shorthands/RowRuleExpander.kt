package app.parsing.css.properties.shorthands

/**
 * Expands the `row-rule` shorthand property — CSS Gap Decorations Level 1 §4.4.
 *
 * Row-axis twin of [ColumnRuleExpander]. Both delegate to the SAME
 * [RuleShorthandExpansion] classifier so the two axes can never drift apart
 * — the gap-decorations family is specified as row/column twins and the IR
 * wire is asserted byte-identical between them.
 *
 * Syntax: row-rule: <width> || <style> || <color>  (order-independent)
 *
 * Examples (all taken from the wpt css-gaps flex corpus):
 * - "5px solid gold"  → row-rule-width: 5px, row-rule-style: solid, row-rule-color: gold
 * - "5px red solid"   → same three longhands, order swapped
 * - "5px solid rgba(255, 0, 0, 0.5)" → the rgba() token survives tokenisation
 *   because the shared tokeniser tracks paren depth.
 * - "2px solid hotpink, 1px dashed grey" → a `<gap-rule-list>`; we model one
 *   rule per axis, so the declaration is handed back unexpanded and recorded
 *   as an honest GenericProperty rather than collapsed to the last rule.
 */
object RowRuleExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> =
        // Only the longhand prefix and the shorthand's own name differ.
        RuleShorthandExpansion.expand(value, prefix = "row-rule", selfName = "row-rule")
}
