package app.parsing.css.properties.shorthands

// A6#14 — clone consolidation: this expander used to carry private
//   `splitPreservingFunctions` (1 copy), byte-identical to TokenizationUtils.tokenizeByWhitespace
//   — its 1 call site now calls the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands the `margin` shorthand property into individual side properties.
 *
 * Uses the same syntax as padding (1-4 values).
 *
 * Examples:
 * - "10px" → all sides = 10px
 * - "10px 20px" → top/bottom = 10px, left/right = 20px
 * - "calc(100% - 20px)" → all sides = calc(100% - 20px)
 */
object MarginExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val values = TokenizationUtils.tokenizeByWhitespace(value.trim())

        return when (values.size) {
            1 -> {
                val all = values[0]
                mapOf(
                    "margin-top" to all,
                    "margin-right" to all,
                    "margin-bottom" to all,
                    "margin-left" to all
                )
            }
            2 -> {
                val vertical = values[0]
                val horizontal = values[1]
                mapOf(
                    "margin-top" to vertical,
                    "margin-right" to horizontal,
                    "margin-bottom" to vertical,
                    "margin-left" to horizontal
                )
            }
            3 -> {
                val top = values[0]
                val horizontal = values[1]
                val bottom = values[2]
                mapOf(
                    "margin-top" to top,
                    "margin-right" to horizontal,
                    "margin-bottom" to bottom,
                    "margin-left" to horizontal
                )
            }
            4 -> {
                mapOf(
                    "margin-top" to values[0],
                    "margin-right" to values[1],
                    "margin-bottom" to values[2],
                    "margin-left" to values[3]
                )
            }
            else -> emptyMap()
        }
    }

}
