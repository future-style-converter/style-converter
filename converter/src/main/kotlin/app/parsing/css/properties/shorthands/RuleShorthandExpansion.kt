package app.parsing.css.properties.shorthands

// The ONE shared "is this colour syntax?" test — named table + hex + every
// CSS Color 4/5 function. Reusing it here means the rule shorthands classify
// colours exactly the way BackgroundExpander and ColorParser do.
import app.parsing.css.properties.primitiveParsers.ColorSyntaxClassifier

/**
 * Shared token classifier for the `column-rule` / `row-rule` shorthands
 * (CSS Gap Decorations Level 1 §4.4; the column form predates it in
 * CSS Multi-column Level 1 §5.4).
 *
 * Both axes MUST classify tokens identically — the whole gap-decorations
 * family is defined as row/column twins and the IR wire is asserted
 * byte-identical between them — so the logic lives here once and each
 * expander only supplies its own longhand prefix.
 *
 * Grammar: `<line-width> || <line-style> || <color>` (order-independent),
 * where `<line-width>` is a `<length>` OR one of `thin | medium | thick`.
 */
object RuleShorthandExpansion {

    // `<line-style>` idents (CSS Backgrounds 3 §4.2), shared by both axes.
    private val styles = setOf(
        "none", "hidden", "dotted", "dashed", "solid", "double",
        "groove", "ridge", "inset", "outset"
    )

    // `<line-width>` keywords. These MUST be classified before the colour
    // branch: they are plain alpha idents, so the old "any remaining ident is
    // a named colour" rule swallowed them and emitted e.g.
    // `column-rule-color: thick` — which the web extractor then rendered as
    // `transparent`, silently painting an invisible rule.
    private val widthKeywords = setOf("thin", "medium", "thick")

    /**
     * Expand one rule shorthand value into `<prefix>-width/-style/-color`.
     *
     * @param value the raw declaration value.
     * @param prefix `"column-rule"` or `"row-rule"`.
     * @param selfName the shorthand's own CSS name, used for the honest-bail
     *        return below.
     */
    fun expand(value: String, prefix: String, selfName: String): Map<String, String> {
        val trimmed = value.trim()

        // `<gap-rule-list>` (css-gaps-1 §4.5) lets a single declaration carry
        // SEVERAL comma-separated rules, e.g. "2px solid hotpink, 1px dashed
        // grey". We model one rule per axis only, so rather than silently
        // collapsing the list to whichever rule happens to come last, hand the
        // declaration back unexpanded: PropertiesParser then finds no longhand
        // parser for the shorthand name and records an honest GenericProperty
        // (`_unmapped: true`) instead of a wrong value.
        if (hasTopLevelComma(trimmed)) return mapOf(selfName to trimmed)

        val result = mutableMapOf<String, String>()

        for (token in tokenize(trimmed)) {
            val lower = token.lowercase()
            when {
                // 1. `<line-style>` first — "inset"/"outset" must never be
                //    mistaken for anything else.
                lower in styles -> result["$prefix-style"] = token
                // 2. `<line-width>` keyword — before the colour branch.
                lower in widthKeywords -> result["$prefix-width"] = token
                // 3. `<line-width>` as a bare number with an optional unit.
                token.matches(LENGTH_RE) -> result["$prefix-width"] = token
                // 4. `<color>` — the shared classifier, so hex, rgb()/hsl()
                //    AND the CSS Color 4/5 functions (oklch/lab/color-mix/…)
                //    all land on the colour longhand instead of being dropped.
                ColorSyntaxClassifier.isColorValue(token) -> result["$prefix-color"] = token
                // Anything else (var(), calc(), junk) is dropped here — the
                // longhand parsers remain the single source of truth for what
                // is actually representable.
            }
        }

        // Nothing was recognised at all (e.g. `column-rule: repeat(5, 100px)`,
        // the other `<gap-rule-list>` spelling, or a bare `var()`
        // substitution). Dropping the declaration outright would make it
        // invisible to the tracker AND to the report, so hand it back for the
        // same GenericProperty(`_unmapped: true`) treatment as a rule-list.
        if (result.isEmpty() && trimmed.isNotEmpty()) return mapOf(selfName to trimmed)

        return result
    }

    // `<length>`: optional sign, digits with an optional fractional part, and
    // an optional CSS unit. A bare `0` is legal too.
    private val LENGTH_RE =
        """^[+-]?(\d+\.?\d*|\.\d+)(px|em|rem|%|pt|cm|mm|in|pc|ex|ch|vw|vh|vmin|vmax|q)?$"""
            .toRegex(RegexOption.IGNORE_CASE)

    /** True when [value] contains a comma OUTSIDE any parenthesis — i.e. a
     *  `<gap-rule-list>` separator rather than an `rgba(…)` argument comma. */
    private fun hasTopLevelComma(value: String): Boolean {
        var depth = 0
        for (char in value) {
            when {
                char == '(' -> depth++
                char == ')' -> depth--
                char == ',' && depth == 0 -> return true
            }
        }
        return false
    }

    /**
     * Whitespace tokeniser that respects parenthesis nesting, so
     * "rgba(255, 0, 0, 0.5)" survives as ONE token.
     */
    private fun tokenize(value: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0

        for (char in value) {
            when {
                // Opening paren: descend a level, keep the char.
                char == '(' -> {
                    parenDepth++
                    current.append(char)
                }
                // Closing paren: ascend a level, keep the char.
                char == ')' -> {
                    parenDepth--
                    current.append(char)
                }
                // Only TOP-LEVEL whitespace separates tokens.
                char.isWhitespace() && parenDepth == 0 -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                }
                // Everything else accumulates into the current token.
                else -> current.append(char)
            }
        }

        // Flush the trailing token.
        if (current.isNotEmpty()) tokens.add(current.toString())

        return tokens
    }
}
