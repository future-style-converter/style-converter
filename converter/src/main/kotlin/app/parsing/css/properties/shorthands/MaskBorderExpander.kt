package app.parsing.css.properties.shorthands

/**
 * Expands `mask-border` shorthand into mask-border-* longhands.
 * Syntax: <source> [<slice> [/ <width> [/ <outset>]]] [<repeat>] [<mode>]
 */
object MaskBorderExpander : ShorthandExpander {
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")
    private val repeatKeywords = setOf("stretch", "repeat", "round", "space")
    private val modeKeywords = setOf("alpha", "luminance")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed.lowercase() in globalKeywords) {
            return mapOf(
                "mask-border-source" to trimmed,
                "mask-border-slice" to trimmed,
                "mask-border-width" to trimmed,
                "mask-border-outset" to trimmed,
                "mask-border-repeat" to trimmed,
                "mask-border-mode" to trimmed
            )
        }

        val result = mutableMapOf<String, String>()

        // Normalize spaces around slashes: "30 / 10px" -> "30/10px"
        val normalized = trimmed.replace(Regex("\\s*/\\s*"), "/")
        val tokens = tokenize(normalized)
        var i = 0

        // First token should be the source (url, gradient, or none)
        if (i < tokens.size) {
            val token = tokens[i]
            val lower = token.lowercase()
            if (lower == "none" || lower.startsWith("url(") ||
                lower.startsWith("linear-gradient(") || lower.startsWith("radial-gradient(")) {
                result["mask-border-source"] = token
                i++
            }
        }

        // Parse slice / width / outset if present
        while (i < tokens.size) {
            val token = tokens[i]
            val lower = token.lowercase()

            when {
                lower in repeatKeywords -> {
                    result["mask-border-repeat"] = token
                    i++
                }
                lower in modeKeywords -> {
                    result["mask-border-mode"] = token
                    i++
                }
                token.contains("/") -> {
                    // slice / width / outset syntax
                    val slashParts = token.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                    if (slashParts.isNotEmpty()) result["mask-border-slice"] = slashParts[0]
                    if (slashParts.size > 1) result["mask-border-width"] = slashParts[1]
                    if (slashParts.size > 2) result["mask-border-outset"] = slashParts[2]
                    i++
                }
                isNumeric(lower) -> {
                    if (!result.containsKey("mask-border-slice")) {
                        result["mask-border-slice"] = token
                    }
                    i++
                }
                else -> i++
            }
        }

        return result
    }

    private fun isNumeric(value: String): Boolean {
        return value.matches(Regex("-?[\\d.]+[a-z%]*")) || value == "fill"
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
