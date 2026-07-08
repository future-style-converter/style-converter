package app.parsing.css.properties.shorthands

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
        val tokens = tokenize(value)
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

        val tokens = tokenize(trimmed)
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

        val tokens = tokenize(trimmed)
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

        val tokens = tokenize(trimmed)
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
