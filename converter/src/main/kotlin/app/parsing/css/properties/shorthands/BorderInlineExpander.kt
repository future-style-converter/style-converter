package app.parsing.css.properties.shorthands

// A6#14 — clone consolidation: this expander used to carry private
//   `tokenize` (1 copy), byte-identical to TokenizationUtils.tokenizeBySpace
//   — its 1 call site now calls the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands `border-inline` shorthand into `border-inline-start-*` and `border-inline-end-*` longhands.
 */
object BorderInlineExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed in setOf("inherit", "initial", "unset", "revert")) {
            return mapOf(
                "border-inline-start-width" to trimmed,
                "border-inline-start-style" to trimmed,
                "border-inline-start-color" to trimmed,
                "border-inline-end-width" to trimmed,
                "border-inline-end-style" to trimmed,
                "border-inline-end-color" to trimmed
            )
        }

        val tokens = TokenizationUtils.tokenizeBySpace(trimmed)
        var width: String? = null
        var style: String? = null
        var color: String? = null

        val styleKeywords = setOf("none", "hidden", "dotted", "dashed", "solid", "double", "groove", "ridge", "inset", "outset")

        for (token in tokens) {
            val lower = token.lowercase()
            when {
                lower in styleKeywords -> style = token
                isWidth(lower) -> width = token
                else -> color = token
            }
        }

        val result = mutableMapOf<String, String>()
        width?.let {
            result["border-inline-start-width"] = it
            result["border-inline-end-width"] = it
        }
        style?.let {
            result["border-inline-start-style"] = it
            result["border-inline-end-style"] = it
        }
        color?.let {
            result["border-inline-start-color"] = it
            result["border-inline-end-color"] = it
        }
        return result
    }

    private fun isWidth(value: String): Boolean {
        return value in setOf("thin", "medium", "thick") ||
               value.matches(Regex("-?[\\d.]+[a-z]*"))
    }

}
