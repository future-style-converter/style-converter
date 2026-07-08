package app.parsing.css.properties.shorthands

/**
 * Expands `border-block` shorthand into `border-block-start-*` and `border-block-end-*` longhands.
 */
object BorderBlockExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed in setOf("inherit", "initial", "unset", "revert")) {
            return mapOf(
                "border-block-start-width" to trimmed,
                "border-block-start-style" to trimmed,
                "border-block-start-color" to trimmed,
                "border-block-end-width" to trimmed,
                "border-block-end-style" to trimmed,
                "border-block-end-color" to trimmed
            )
        }

        val tokens = tokenize(trimmed)
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
            result["border-block-start-width"] = it
            result["border-block-end-width"] = it
        }
        style?.let {
            result["border-block-start-style"] = it
            result["border-block-end-style"] = it
        }
        color?.let {
            result["border-block-start-color"] = it
            result["border-block-end-color"] = it
        }
        return result
    }

    private fun isWidth(value: String): Boolean {
        return value in setOf("thin", "medium", "thick") ||
               value.matches(Regex("-?[\\d.]+[a-z]*"))
    }

    private fun tokenize(value: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0
        for (char in value) {
            when {
                char == '(' -> { depth++; current.append(char) }
                char == ')' -> { depth--; current.append(char) }
                char == ' ' && depth == 0 -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }
}
