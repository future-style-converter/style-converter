package app.parsing.css.properties.shorthands

// A6#14 — clone consolidation: this expander used to carry private
//   `tokenize` (4 identical copies), byte-identical to TokenizationUtils.tokenizeBySpace
//   — its 4 call sites now call the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands `border-block-start` shorthand into border-block-start-* longhands.
 */
object BorderBlockStartExpander : ShorthandExpander {
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")
    private val styleKeywords = setOf("none", "hidden", "dotted", "dashed", "solid", "double", "groove", "ridge", "inset", "outset")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed.lowercase() in globalKeywords) {
            return mapOf(
                "border-block-start-width" to trimmed,
                "border-block-start-style" to trimmed,
                "border-block-start-color" to trimmed
            )
        }

        return parseBorderShorthand(trimmed, "border-block-start")
    }

    private fun parseBorderShorthand(value: String, prefix: String): Map<String, String> {
        val tokens = TokenizationUtils.tokenizeBySpace(value)
        var width: String? = null
        var style: String? = null
        var color: String? = null

        for (token in tokens) {
            val lower = token.lowercase()
            when {
                lower in styleKeywords -> style = token
                isWidth(lower) -> width = token
                else -> color = token
            }
        }

        val result = mutableMapOf<String, String>()
        width?.let { result["$prefix-width"] = it }
        style?.let { result["$prefix-style"] = it }
        color?.let { result["$prefix-color"] = it }
        return result
    }

    private fun isWidth(value: String): Boolean {
        return value in setOf("thin", "medium", "thick") ||
               value.matches(Regex("-?[\\d.]+[a-z]*"))
    }

}

/**
 * Expands `border-block-end` shorthand into border-block-end-* longhands.
 */
object BorderBlockEndExpander : ShorthandExpander {
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")
    private val styleKeywords = setOf("none", "hidden", "dotted", "dashed", "solid", "double", "groove", "ridge", "inset", "outset")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed.lowercase() in globalKeywords) {
            return mapOf(
                "border-block-end-width" to trimmed,
                "border-block-end-style" to trimmed,
                "border-block-end-color" to trimmed
            )
        }

        val tokens = TokenizationUtils.tokenizeBySpace(trimmed)
        var width: String? = null
        var style: String? = null
        var color: String? = null

        for (token in tokens) {
            val lower = token.lowercase()
            when {
                lower in styleKeywords -> style = token
                isWidth(lower) -> width = token
                else -> color = token
            }
        }

        val result = mutableMapOf<String, String>()
        width?.let { result["border-block-end-width"] = it }
        style?.let { result["border-block-end-style"] = it }
        color?.let { result["border-block-end-color"] = it }
        return result
    }

    private fun isWidth(value: String): Boolean {
        return value in setOf("thin", "medium", "thick") ||
               value.matches(Regex("-?[\\d.]+[a-z]*"))
    }

}

/**
 * Expands `border-inline-start` shorthand into border-inline-start-* longhands.
 */
object BorderInlineStartExpander : ShorthandExpander {
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")
    private val styleKeywords = setOf("none", "hidden", "dotted", "dashed", "solid", "double", "groove", "ridge", "inset", "outset")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed.lowercase() in globalKeywords) {
            return mapOf(
                "border-inline-start-width" to trimmed,
                "border-inline-start-style" to trimmed,
                "border-inline-start-color" to trimmed
            )
        }

        val tokens = TokenizationUtils.tokenizeBySpace(trimmed)
        var width: String? = null
        var style: String? = null
        var color: String? = null

        for (token in tokens) {
            val lower = token.lowercase()
            when {
                lower in styleKeywords -> style = token
                isWidth(lower) -> width = token
                else -> color = token
            }
        }

        val result = mutableMapOf<String, String>()
        width?.let { result["border-inline-start-width"] = it }
        style?.let { result["border-inline-start-style"] = it }
        color?.let { result["border-inline-start-color"] = it }
        return result
    }

    private fun isWidth(value: String): Boolean {
        return value in setOf("thin", "medium", "thick") ||
               value.matches(Regex("-?[\\d.]+[a-z]*"))
    }

}

/**
 * Expands `border-inline-end` shorthand into border-inline-end-* longhands.
 */
object BorderInlineEndExpander : ShorthandExpander {
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")
    private val styleKeywords = setOf("none", "hidden", "dotted", "dashed", "solid", "double", "groove", "ridge", "inset", "outset")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed.lowercase() in globalKeywords) {
            return mapOf(
                "border-inline-end-width" to trimmed,
                "border-inline-end-style" to trimmed,
                "border-inline-end-color" to trimmed
            )
        }

        val tokens = TokenizationUtils.tokenizeBySpace(trimmed)
        var width: String? = null
        var style: String? = null
        var color: String? = null

        for (token in tokens) {
            val lower = token.lowercase()
            when {
                lower in styleKeywords -> style = token
                isWidth(lower) -> width = token
                else -> color = token
            }
        }

        val result = mutableMapOf<String, String>()
        width?.let { result["border-inline-end-width"] = it }
        style?.let { result["border-inline-end-style"] = it }
        color?.let { result["border-inline-end-color"] = it }
        return result
    }

    private fun isWidth(value: String): Boolean {
        return value in setOf("thin", "medium", "thick") ||
               value.matches(Regex("-?[\\d.]+[a-z]*"))
    }

}
