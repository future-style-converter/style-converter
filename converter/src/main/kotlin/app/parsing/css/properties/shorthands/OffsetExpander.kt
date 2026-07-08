package app.parsing.css.properties.shorthands

/**
 * Expands `offset` shorthand into offset-* longhands.
 * Syntax: [offset-position] [offset-path] [offset-distance] [offset-rotate] / [offset-anchor]
 */
object OffsetExpander : ShorthandExpander {
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed.lowercase() in globalKeywords) {
            return mapOf(
                "offset-position" to trimmed,
                "offset-path" to trimmed,
                "offset-distance" to trimmed,
                "offset-rotate" to trimmed,
                "offset-anchor" to trimmed
            )
        }

        val result = mutableMapOf<String, String>()

        // Split by / for anchor
        val slashParts = trimmed.split("/", limit = 2)
        if (slashParts.size == 2) {
            result["offset-anchor"] = slashParts[1].trim()
        }

        val mainPart = slashParts[0].trim()
        val tokens = tokenize(mainPart)

        for (token in tokens) {
            val lower = token.lowercase()
            when {
                // offset-path: path(), url(), ray(), basic shapes
                lower.startsWith("path(") || lower.startsWith("url(") ||
                lower.startsWith("ray(") || lower.startsWith("circle(") ||
                lower.startsWith("ellipse(") || lower.startsWith("polygon(") ||
                lower.startsWith("inset(") -> {
                    result["offset-path"] = token
                }
                // offset-rotate: auto, reverse, angle
                lower == "auto" || lower == "reverse" || lower.startsWith("auto ") ||
                lower.endsWith("deg") || lower.endsWith("rad") || lower.endsWith("turn") || lower.endsWith("grad") -> {
                    result["offset-rotate"] = token
                }
                // offset-distance: length or percentage
                lower.matches(Regex("-?[\\d.]+[a-z%]+")) -> {
                    result["offset-distance"] = token
                }
                // offset-position: keywords or positions
                lower in setOf("auto", "top", "bottom", "left", "right", "center") -> {
                    result["offset-position"] = token
                }
                else -> {
                    // Default to offset-path for unrecognized
                    if (!result.containsKey("offset-path")) {
                        result["offset-path"] = token
                    }
                }
            }
        }

        return result
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
