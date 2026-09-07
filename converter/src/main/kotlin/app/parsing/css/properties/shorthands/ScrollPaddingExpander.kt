package app.parsing.css.properties.shorthands

// A6#14 — clone consolidation: this expander used to carry private
//   `splitPreservingFunctions` (3 identical copies), byte-identical to TokenizationUtils.tokenizeByWhitespace
//   — its 3 call sites now call the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands the `scroll-padding` shorthand property.
 *
 * Syntax: scroll-padding: <top> <right>? <bottom>? <left>?
 * Uses same 1-4 value syntax as margin/padding.
 */
object ScrollPaddingExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = TokenizationUtils.tokenizeByWhitespace(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "scroll-padding-top" to parts[0],
                "scroll-padding-right" to parts[0],
                "scroll-padding-bottom" to parts[0],
                "scroll-padding-left" to parts[0]
            )
            2 -> mapOf(
                "scroll-padding-top" to parts[0],
                "scroll-padding-right" to parts[1],
                "scroll-padding-bottom" to parts[0],
                "scroll-padding-left" to parts[1]
            )
            3 -> mapOf(
                "scroll-padding-top" to parts[0],
                "scroll-padding-right" to parts[1],
                "scroll-padding-bottom" to parts[2],
                "scroll-padding-left" to parts[1]
            )
            4 -> mapOf(
                "scroll-padding-top" to parts[0],
                "scroll-padding-right" to parts[1],
                "scroll-padding-bottom" to parts[2],
                "scroll-padding-left" to parts[3]
            )
            else -> emptyMap()
        }
    }

}

/**
 * Expands the `scroll-padding-block` shorthand property.
 */
object ScrollPaddingBlockExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = TokenizationUtils.tokenizeByWhitespace(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "scroll-padding-block-start" to parts[0],
                "scroll-padding-block-end" to parts[0]
            )
            2 -> mapOf(
                "scroll-padding-block-start" to parts[0],
                "scroll-padding-block-end" to parts[1]
            )
            else -> emptyMap()
        }
    }

}

/**
 * Expands the `scroll-padding-inline` shorthand property.
 */
object ScrollPaddingInlineExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = TokenizationUtils.tokenizeByWhitespace(value.trim())

        return when (parts.size) {
            1 -> mapOf(
                "scroll-padding-inline-start" to parts[0],
                "scroll-padding-inline-end" to parts[0]
            )
            2 -> mapOf(
                "scroll-padding-inline-start" to parts[0],
                "scroll-padding-inline-end" to parts[1]
            )
            else -> emptyMap()
        }
    }

}
