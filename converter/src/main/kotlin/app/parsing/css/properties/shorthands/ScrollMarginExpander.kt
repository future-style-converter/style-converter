package app.parsing.css.properties.shorthands

// A6#14 — clone consolidation: this expander used to carry private
//   `splitPreservingFunctions` (3 identical copies), byte-identical to TokenizationUtils.tokenizeByWhitespace
//   — its 3 call sites now call the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands the `scroll-margin` shorthand property.
 *
 * Syntax: scroll-margin: <top> <right>? <bottom>? <left>?
 * Uses same 1-4 value syntax as margin/padding.
 */
object ScrollMarginExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = TokenizationUtils.tokenizeByWhitespace(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "scroll-margin-top" to parts[0],
                "scroll-margin-right" to parts[0],
                "scroll-margin-bottom" to parts[0],
                "scroll-margin-left" to parts[0]
            )
            2 -> mapOf(
                "scroll-margin-top" to parts[0],
                "scroll-margin-right" to parts[1],
                "scroll-margin-bottom" to parts[0],
                "scroll-margin-left" to parts[1]
            )
            3 -> mapOf(
                "scroll-margin-top" to parts[0],
                "scroll-margin-right" to parts[1],
                "scroll-margin-bottom" to parts[2],
                "scroll-margin-left" to parts[1]
            )
            4 -> mapOf(
                "scroll-margin-top" to parts[0],
                "scroll-margin-right" to parts[1],
                "scroll-margin-bottom" to parts[2],
                "scroll-margin-left" to parts[3]
            )
            else -> emptyMap()
        }
    }

}

/**
 * Expands the `scroll-margin-block` shorthand property.
 */
object ScrollMarginBlockExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = TokenizationUtils.tokenizeByWhitespace(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "scroll-margin-block-start" to parts[0],
                "scroll-margin-block-end" to parts[0]
            )
            2 -> mapOf(
                "scroll-margin-block-start" to parts[0],
                "scroll-margin-block-end" to parts[1]
            )
            else -> emptyMap()
        }
    }

}

/**
 * Expands the `scroll-margin-inline` shorthand property.
 */
object ScrollMarginInlineExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = TokenizationUtils.tokenizeByWhitespace(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "scroll-margin-inline-start" to parts[0],
                "scroll-margin-inline-end" to parts[0]
            )
            2 -> mapOf(
                "scroll-margin-inline-start" to parts[0],
                "scroll-margin-inline-end" to parts[1]
            )
            else -> emptyMap()
        }
    }

}
