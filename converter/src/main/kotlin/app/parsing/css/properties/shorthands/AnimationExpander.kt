package app.parsing.css.properties.shorthands

/**
 * Expands the `animation` shorthand property into its longhand equivalents.
 *
 * Syntax: <name> <duration> [<timing-function>] [<delay>] [<iteration-count>] [<direction>] [<fill-mode>] [<play-state>]
 *
 * Examples:
 * - "fadeIn 1s" → name: fadeIn, duration: 1s
 * - "slideUp 0.5s ease-in-out" → name: slideUp, duration: 0.5s, timing: ease-in-out
 * - "pulse 2s infinite" → name: pulse, duration: 2s, iteration: infinite
 * - "fadeIn 1s, slideUp 0.5s" → Multiple animations
 */
object AnimationExpander : ShorthandExpander {

    private val timingFunctions = setOf(
        "ease", "ease-in", "ease-out", "ease-in-out", "linear", "step-start", "step-end"
    )

    private val directions = setOf(
        "normal", "reverse", "alternate", "alternate-reverse"
    )

    private val fillModes = setOf(
        "none", "forwards", "backwards", "both"
    )

    private val playStates = setOf(
        "running", "paused"
    )

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()

        // Handle global keywords
        if (trimmed in setOf("inherit", "initial", "unset", "revert", "none")) {
            return mapOf(
                "animation-name" to trimmed,
                "animation-duration" to trimmed,
                "animation-timing-function" to trimmed,
                "animation-delay" to trimmed,
                "animation-iteration-count" to trimmed,
                "animation-direction" to trimmed,
                "animation-fill-mode" to trimmed,
                "animation-play-state" to trimmed
            )
        }

        // Split by top-level commas for multiple animations
        val animations = splitByTopLevelComma(trimmed)

        val names = mutableListOf<String>()
        val durations = mutableListOf<String>()
        val timings = mutableListOf<String>()
        val delays = mutableListOf<String>()
        val iterations = mutableListOf<String>()
        val directionsList = mutableListOf<String>()
        val fills = mutableListOf<String>()
        val states = mutableListOf<String>()

        for (animation in animations) {
            val parsed = parseAnimation(animation.trim())
            names.add(parsed.name)
            durations.add(parsed.duration)
            timings.add(parsed.timing)
            delays.add(parsed.delay)
            iterations.add(parsed.iteration)
            directionsList.add(parsed.direction)
            fills.add(parsed.fillMode)
            states.add(parsed.playState)
        }

        return mapOf(
            "animation-name" to names.joinToString(", "),
            "animation-duration" to durations.joinToString(", "),
            "animation-timing-function" to timings.joinToString(", "),
            "animation-delay" to delays.joinToString(", "),
            "animation-iteration-count" to iterations.joinToString(", "),
            "animation-direction" to directionsList.joinToString(", "),
            "animation-fill-mode" to fills.joinToString(", "),
            "animation-play-state" to states.joinToString(", ")
        )
    }

    private data class ParsedAnimation(
        val name: String = "none",
        val duration: String = "0s",
        val timing: String = "ease",
        val delay: String = "0s",
        val iteration: String = "1",
        val direction: String = "normal",
        val fillMode: String = "none",
        val playState: String = "running"
    )

    private fun parseAnimation(value: String): ParsedAnimation {
        val tokens = tokenize(value)
        if (tokens.isEmpty()) return ParsedAnimation()

        var name: String? = null
        var duration = "0s"
        var timing = "ease"
        var delay = "0s"
        var iteration = "1"
        var direction = "normal"
        var fillMode = "none"
        var playState = "running"

        var timeCount = 0

        for (token in tokens) {
            val lower = token.lowercase()

            when {
                // Timing function
                lower in timingFunctions || lower.startsWith("cubic-bezier(") || lower.startsWith("steps(") -> {
                    timing = token
                }
                // Direction
                lower in directions -> direction = token
                // Fill mode
                lower in fillModes && lower != "none" -> fillMode = token
                // Play state
                lower in playStates -> playState = token
                // Iteration count
                lower == "infinite" || isNumber(token) -> iteration = token
                // Time value
                isTimeValue(token) -> {
                    if (timeCount == 0) {
                        duration = token
                    } else {
                        delay = token
                    }
                    timeCount++
                }
                // Animation name (anything else that's not a keyword)
                else -> {
                    if (name == null && lower != "none") {
                        name = token
                    }
                }
            }
        }

        return ParsedAnimation(
            name = name ?: "none",
            duration = duration,
            timing = timing,
            delay = delay,
            iteration = iteration,
            direction = direction,
            fillMode = fillMode,
            playState = playState
        )
    }

    private fun isTimeValue(token: String): Boolean {
        val lower = token.lowercase()
        return lower.matches(Regex("-?[\\d.]+m?s"))
    }

    private fun isNumber(token: String): Boolean {
        return token.matches(Regex("-?[\\d.]+"))
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
