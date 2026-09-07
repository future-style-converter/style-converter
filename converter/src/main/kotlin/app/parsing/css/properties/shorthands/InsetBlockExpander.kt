package app.parsing.css.properties.shorthands

// A6#14 — clone consolidation: this expander used to carry private
//   `splitPreservingFunctions` (1 copy), byte-identical to TokenizationUtils.tokenizeByWhitespace
//   — its 1 call site now calls the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands the `inset-block` shorthand property.
 *
 * Syntax: inset-block: <start> <end>?
 *
 * Examples:
 * - "10px" → inset-block-start: 10px, inset-block-end: 10px
 * - "10px 20px" → inset-block-start: 10px, inset-block-end: 20px
 */
object InsetBlockExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = TokenizationUtils.tokenizeByWhitespace(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "inset-block-start" to parts[0],
                "inset-block-end" to parts[0]
            )
            2 -> mapOf(
                "inset-block-start" to parts[0],
                "inset-block-end" to parts[1]
            )
            else -> emptyMap()
        }
    }

}
