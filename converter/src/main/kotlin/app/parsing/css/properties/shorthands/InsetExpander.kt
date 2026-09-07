package app.parsing.css.properties.shorthands

// A6#14 — clone consolidation: this expander used to carry private
//   `splitPreservingFunctions` (1 copy), byte-identical to TokenizationUtils.tokenizeByWhitespace
//   — its 1 call site now calls the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands the `inset` shorthand property.
 *
 * Syntax: inset: <top> <right>? <bottom>? <left>?
 * Uses same 1-4 value syntax as margin/padding.
 *
 * Examples:
 * - "10px" → top/right/bottom/left = 10px
 * - "10px 20px" → top/bottom = 10px, left/right = 20px
 * - "10px 20px 15px" → top = 10px, left/right = 20px, bottom = 15px
 * - "10px 20px 15px 5px" → top = 10px, right = 20px, bottom = 15px, left = 5px
 */
object InsetExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = TokenizationUtils.tokenizeByWhitespace(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "top" to parts[0],
                "right" to parts[0],
                "bottom" to parts[0],
                "left" to parts[0]
            )
            2 -> mapOf(
                "top" to parts[0],
                "right" to parts[1],
                "bottom" to parts[0],
                "left" to parts[1]
            )
            3 -> mapOf(
                "top" to parts[0],
                "right" to parts[1],
                "bottom" to parts[2],
                "left" to parts[1]
            )
            4 -> mapOf(
                "top" to parts[0],
                "right" to parts[1],
                "bottom" to parts[2],
                "left" to parts[3]
            )
            else -> emptyMap()
        }
    }

}
