package app.parsing.css.properties.shorthands

// A6#14 — clone consolidation: this expander used to carry private
//   `tokenize` (1 copy), byte-identical to TokenizationUtils.tokenizeBySpace
//   — its 1 call site now calls the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands `list-style` shorthand into longhands.
 * Syntax: <list-style-type> || <list-style-position> || <list-style-image>
 */
object ListStyleExpander : ShorthandExpander {

    private val positionKeywords = setOf("inside", "outside")
    private val typeKeywords = setOf(
        "disc", "circle", "square", "decimal", "decimal-leading-zero",
        "lower-roman", "upper-roman", "lower-greek", "lower-latin", "upper-latin",
        "armenian", "georgian", "lower-alpha", "upper-alpha", "none"
    )

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed in setOf("inherit", "initial", "unset", "revert")) {
            return mapOf(
                "list-style-type" to trimmed,
                "list-style-position" to trimmed,
                "list-style-image" to trimmed
            )
        }

        val tokens = TokenizationUtils.tokenizeBySpace(trimmed)
        var type: String? = null
        var position: String? = null
        var image: String? = null

        for (token in tokens) {
            val lower = token.lowercase()
            when {
                lower in positionKeywords -> position = token
                lower.startsWith("url(") -> image = token
                lower == "none" && image == null && type != null -> image = "none"
                lower in typeKeywords || type == null -> type = token
            }
        }

        val result = mutableMapOf<String, String>()
        type?.let { result["list-style-type"] = it }
        position?.let { result["list-style-position"] = it }
        image?.let { result["list-style-image"] = it }
        return result
    }

}
