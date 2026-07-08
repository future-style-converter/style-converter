package app.parsing.css.properties.shorthands

/**
 * Expands the `transition` shorthand property into its longhand equivalents.
 *
 * Syntax: <property> <duration> [<timing-function>] [<delay>]
 *
 * Examples:
 * - "all 0.3s" → property: all, duration: 0.3s
 * - "opacity 0.2s ease-in" → property: opacity, duration: 0.2s, timing: ease-in
 * - "transform 0.3s ease 0.1s" → property: transform, duration: 0.3s, timing: ease, delay: 0.1s
 * - "opacity 0.2s, transform 0.3s" → Multiple transitions
 */
object TransitionExpander : ShorthandExpander {

    private val timingFunctions = setOf(
        "ease", "ease-in", "ease-out", "ease-in-out", "linear", "step-start", "step-end"
    )

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()

        // Handle global keywords
        if (trimmed in setOf("inherit", "initial", "unset", "revert", "none")) {
            return mapOf(
                "transition-property" to trimmed,
                "transition-duration" to trimmed,
                "transition-timing-function" to trimmed,
                "transition-delay" to trimmed
            )
        }

        // Split by top-level commas for multiple transitions
        val transitions = splitByTopLevelComma(trimmed)

        val properties = mutableListOf<String>()
        val durations = mutableListOf<String>()
        val timings = mutableListOf<String>()
        val delays = mutableListOf<String>()

        for (transition in transitions) {
            val parsed = parseTransition(transition.trim())
            properties.add(parsed.property)
            durations.add(parsed.duration)
            timings.add(parsed.timing)
            delays.add(parsed.delay)
        }

        return mapOf(
            "transition-property" to properties.joinToString(", "),
            "transition-duration" to durations.joinToString(", "),
            "transition-timing-function" to timings.joinToString(", "),
            "transition-delay" to delays.joinToString(", ")
        )
    }

    private data class ParsedTransition(
        val property: String = "all",
        val duration: String = "0s",
        val timing: String = "ease",
        val delay: String = "0s"
    )

    private fun parseTransition(value: String): ParsedTransition {
        val tokens = tokenize(value)
        if (tokens.isEmpty()) return ParsedTransition()

        var property = "all"
        var duration = "0s"
        var timing = "ease"
        var delay = "0s"

        var timeCount = 0

        for (token in tokens) {
            val lower = token.lowercase()

            when {
                // Timing function (including cubic-bezier and steps)
                lower in timingFunctions || lower.startsWith("cubic-bezier(") || lower.startsWith("steps(") -> {
                    timing = token
                }
                // Time value
                isTimeValue(token) -> {
                    if (timeCount == 0) {
                        duration = token
                    } else {
                        delay = token
                    }
                    timeCount++
                }
                // Property name (anything else)
                else -> {
                    if (property == "all" && !isTimeValue(token)) {
                        property = token
                    }
                }
            }
        }

        return ParsedTransition(property, duration, timing, delay)
    }

    private fun isTimeValue(token: String): Boolean {
        val lower = token.lowercase()
        return lower.matches(Regex("-?[\\d.]+m?s"))
    }

    private fun tokenize(value: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (char in value) {
            when {
                char == '(' -> {
                    depth++
                    current.append(char)
                }
                char == ')' -> {
                    depth--
                    current.append(char)
                }
                char == ' ' && depth == 0 -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }

    private fun splitByTopLevelComma(value: String): List<String> {
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (char in value) {
            when {
                char == '(' -> {
                    depth++
                    current.append(char)
                }
                char == ')' -> {
                    depth--
                    current.append(char)
                }
                char == ',' && depth == 0 -> {
                    parts.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            parts.add(current.toString().trim())
        }

        return parts
    }
}
