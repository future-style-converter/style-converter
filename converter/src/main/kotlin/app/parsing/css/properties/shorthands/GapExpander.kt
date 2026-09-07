package app.parsing.css.properties.shorthands

// A6#14 — clone consolidation: this expander used to carry private
//   `splitPreservingFunctions` (1 copy), byte-identical to TokenizationUtils.tokenizeByWhitespace
//   — its 1 call site now calls the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands the `gap` shorthand property.
 *
 * Syntax: gap: <row-gap> <column-gap>?
 *
 * Examples:
 * - "10px" → row-gap: 10px, column-gap: 10px
 * - "10px 20px" → row-gap: 10px, column-gap: 20px
 */
object GapExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = TokenizationUtils.tokenizeByWhitespace(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "row-gap" to parts[0],
                "column-gap" to parts[0]
            )
            2 -> mapOf(
                "row-gap" to parts[0],
                "column-gap" to parts[1]
            )
            else -> emptyMap()
        }
    }

}
