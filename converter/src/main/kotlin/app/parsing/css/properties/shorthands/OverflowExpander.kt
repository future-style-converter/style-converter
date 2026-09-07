package app.parsing.css.properties.shorthands

// A6#14 — clone consolidation: this expander used to carry private
//   `splitPreservingFunctions` (1 copy), byte-identical to TokenizationUtils.tokenizeByWhitespace
//   — its 1 call site now calls the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands the `overflow` shorthand property.
 *
 * Syntax: overflow: <x> <y>?
 *
 * Examples:
 * - "hidden" → overflow-x: hidden, overflow-y: hidden
 * - "auto scroll" → overflow-x: auto, overflow-y: scroll
 */
object OverflowExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = TokenizationUtils.tokenizeByWhitespace(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "overflow-x" to parts[0],
                "overflow-y" to parts[0]
            )
            2 -> mapOf(
                "overflow-x" to parts[0],
                "overflow-y" to parts[1]
            )
            else -> emptyMap()
        }
    }

}
