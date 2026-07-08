package app.parsing.css.properties.shorthands

/**
 * Expands the `flex` shorthand property.
 *
 * Syntax: flex: <grow> <shrink>? <basis>?
 *
 * Examples:
 * - "1" → flex-grow: 1
 * - "1 1" → flex-grow: 1, flex-shrink: 1
 * - "1 1 auto" → flex-grow: 1, flex-shrink: 1, flex-basis: auto
 * - "auto" → flex-grow: 1, flex-shrink: 1, flex-basis: auto
 * - "none" → flex-grow: 0, flex-shrink: 0, flex-basis: auto
 */
object FlexExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim().lowercase()

        return when (trimmed) {
            "auto" -> mapOf(
                "flex-grow" to "1",
                "flex-shrink" to "1",
                "flex-basis" to "auto"
            )
            "none" -> mapOf(
                "flex-grow" to "0",
                "flex-shrink" to "0",
                "flex-basis" to "auto"
            )
            "initial" -> mapOf(
                "flex-grow" to "0",
                "flex-shrink" to "1",
                "flex-basis" to "auto"
            )
            else -> {
                val parts = splitPreservingFunctions(value.trim())
                val result = mutableMapOf<String, String>()

                when (parts.size) {
                    1 -> {
                        // Single value: could be grow or basis
                        val v = parts[0]
                        if (v.matches("""^\d+(\.\d+)?$""".toRegex())) {
                            result["flex-grow"] = v
                        } else {
                            result["flex-basis"] = v
                        }
                    }
                    2 -> {
                        result["flex-grow"] = parts[0]
                        // Second could be shrink or basis
                        if (parts[1].matches("""^\d+(\.\d+)?$""".toRegex())) {
                            result["flex-shrink"] = parts[1]
                        } else {
                            result["flex-basis"] = parts[1]
                        }
                    }
                    3 -> {
                        result["flex-grow"] = parts[0]
                        result["flex-shrink"] = parts[1]
                        result["flex-basis"] = parts[2]
                    }
                }

                result
            }
        }
    }

    private fun splitPreservingFunctions(value: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0

        for (char in value) {
            when {
                char == '(' -> { parenDepth++; current.append(char) }
                char == ')' -> { parenDepth--; current.append(char) }
                char.isWhitespace() && parenDepth == 0 -> {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
