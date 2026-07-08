package app.parsing.css.properties.shorthands

/**
 * Expands the `background` shorthand property into its longhand equivalents.
 *
 * Supports:
 * - Simple colors: "red", "#fff", "rgb(...)", "hsl(...)"
 * - Gradients: "linear-gradient(...)", "radial-gradient(...)"
 * - Images: "url(...)"
 * - Multiple backgrounds separated by commas
 * - Combined values: "url(...) no-repeat center/cover"
 */
object BackgroundExpander : ShorthandExpander {

    private val colorKeywords = setOf(
        "transparent", "currentcolor", "inherit", "initial", "unset",
        "black", "white", "red", "green", "blue", "yellow", "orange", "purple",
        "pink", "gray", "grey", "brown", "cyan", "magenta", "lime", "olive",
        "navy", "teal", "aqua", "fuchsia", "silver", "maroon"
    )

    private val repeatKeywords = setOf(
        "repeat", "repeat-x", "repeat-y", "no-repeat", "space", "round"
    )

    private val attachmentKeywords = setOf(
        "scroll", "fixed", "local"
    )

    private val positionKeywords = setOf(
        "top", "right", "bottom", "left", "center"
    )

    private val boxKeywords = setOf(
        "border-box", "padding-box", "content-box"
    )

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()

        // Handle global keywords
        if (trimmed in setOf("inherit", "initial", "unset", "revert")) {
            return mapOf(
                "background-color" to trimmed,
                "background-image" to trimmed,
                "background-position" to trimmed,
                "background-size" to trimmed,
                "background-repeat" to trimmed,
                "background-attachment" to trimmed,
                "background-origin" to trimmed,
                "background-clip" to trimmed
            )
        }

        // Handle "none"
        if (trimmed == "none") {
            return mapOf("background-image" to "none")
        }

        // Check if it's a simple color value
        if (isSimpleColor(trimmed)) {
            return mapOf("background-color" to trimmed)
        }

        // Check if it's ONLY a gradient or image (no other values like position/repeat)
        if (isPureGradientOrImage(trimmed)) {
            return mapOf("background-image" to trimmed)
        }

        // Parse complex background value
        return parseComplexBackground(trimmed)
    }

    private fun isSimpleColor(value: String): Boolean {
        val lower = value.lowercase()
        // Named colors
        if (lower in colorKeywords) return true
        // Hex colors
        if (value.startsWith("#")) return true
        // RGB/RGBA/HSL/HSLA
        if (lower.startsWith("rgb(") || lower.startsWith("rgba(") ||
            lower.startsWith("hsl(") || lower.startsWith("hsla(")) return true
        return false
    }

    private fun isGradientOrImage(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("url(") ||
               lower.startsWith("linear-gradient(") ||
               lower.startsWith("radial-gradient(") ||
               lower.startsWith("conic-gradient(") ||
               lower.startsWith("repeating-linear-gradient(") ||
               lower.startsWith("repeating-radial-gradient(") ||
               lower.startsWith("repeating-conic-gradient(") ||
               lower.contains("var(")
    }

    /**
     * Check if value is ONLY a gradient or image with no additional tokens.
     * e.g., "url('image.png')" → true
     *       "url('image.png') no-repeat" → false
     *       "linear-gradient(red, blue)" → true
     *       "linear-gradient(red, blue), url('bg.png')" → false (multiple images)
     */
    private fun isPureGradientOrImage(value: String): Boolean {
        val lower = value.lowercase()

        // Check if it starts with a gradient or url
        if (!isGradientOrImage(value)) return false

        // Count parentheses to find where the function ends
        var depth = 0
        var i = 0
        for (char in lower) {
            when (char) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        // Check if there's anything after the closing parenthesis
                        val remaining = value.substring(i + 1).trim()
                        // Empty or only whitespace = pure image
                        return remaining.isEmpty()
                    }
                }
            }
            i++
        }

        return false // Unbalanced parentheses
    }

    private fun parseComplexBackground(value: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // If it contains var(), keep the whole value as background-image
        if (value.contains("var(")) {
            result["background-image"] = value
            return result
        }

        // Split by top-level commas for multiple backgrounds
        val layers = splitByTopLevelComma(value)

        if (layers.size > 1) {
            // Multiple backgrounds - extract just the images from each layer
            val images = mutableListOf<String>()
            val positions = mutableListOf<String>()
            val repeats = mutableListOf<String>()
            val sizes = mutableListOf<String>()

            for (layer in layers) {
                val layerResult = parseSingleLayerBackground(layer)
                layerResult["background-image"]?.let { images.add(it) }
                layerResult["background-position"]?.let { positions.add(it) }
                layerResult["background-repeat"]?.let { repeats.add(it) }
                layerResult["background-size"]?.let { sizes.add(it) }
            }

            if (images.isNotEmpty()) result["background-image"] = images.joinToString(", ")
            if (positions.isNotEmpty()) result["background-position"] = positions.joinToString(", ")
            if (repeats.isNotEmpty()) result["background-repeat"] = repeats.joinToString(", ")
            if (sizes.isNotEmpty()) result["background-size"] = sizes.joinToString(", ")

            return result
        }

        // Single background - delegate to single layer parser
        return parseSingleLayerBackground(value)
    }

    /**
     * Parse a single background layer (no commas).
     */
    private fun parseSingleLayerBackground(value: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val tokens = tokenize(value)

        var image: String? = null
        var position: String? = null
        var size: String? = null
        var repeat: String? = null
        var attachment: String? = null
        var origin: String? = null
        var clip: String? = null
        var color: String? = null

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            val lower = token.lowercase()

            when {
                // Gradient or url
                isGradientOrImage(token) -> image = token

                // Repeat keywords
                lower in repeatKeywords -> {
                    repeat = if (repeat == null) token else "$repeat $token"
                }

                // Attachment
                lower in attachmentKeywords -> attachment = token

                // Box keywords (origin or clip)
                lower in boxKeywords -> {
                    if (origin == null) origin = token
                    else clip = token
                }

                // Position keywords or values
                lower in positionKeywords || isLengthOrPercent(token) -> {
                    val posTokens = mutableListOf(token)
                    // Collect position tokens
                    while (i + 1 < tokens.size &&
                           (tokens[i + 1].lowercase() in positionKeywords || isLengthOrPercent(tokens[i + 1]))) {
                        i++
                        posTokens.add(tokens[i])
                    }
                    // Check for size after /
                    if (i + 1 < tokens.size && tokens[i + 1] == "/") {
                        i++ // skip /
                        position = posTokens.joinToString(" ")
                        // Collect size tokens
                        val sizeTokens = mutableListOf<String>()
                        while (i + 1 < tokens.size &&
                               (tokens[i + 1].lowercase() in setOf("cover", "contain", "auto") ||
                                isLengthOrPercent(tokens[i + 1]))) {
                            i++
                            sizeTokens.add(tokens[i])
                        }
                        if (sizeTokens.isNotEmpty()) {
                            size = sizeTokens.joinToString(" ")
                        }
                    } else {
                        position = posTokens.joinToString(" ")
                    }
                }

                // Size keywords without position
                lower in setOf("cover", "contain", "auto") -> size = token

                // Color
                isSimpleColor(token) -> color = token
            }

            i++
        }

        // Build result map
        image?.let { result["background-image"] = it }
        position?.let { result["background-position"] = it }
        size?.let { result["background-size"] = it }
        repeat?.let { result["background-repeat"] = it }
        attachment?.let { result["background-attachment"] = it }
        origin?.let { result["background-origin"] = it }
        clip?.let { result["background-clip"] = it }
        color?.let { result["background-color"] = it }

        // If nothing was parsed, treat the whole value as background-image
        if (result.isEmpty()) {
            result["background-image"] = value
        }

        return result
    }

    private fun isLengthOrPercent(token: String): Boolean {
        val lower = token.lowercase()
        return lower.matches(Regex("-?[\\d.]+(%|px|em|rem|vh|vw|vmin|vmax|ch|ex|cm|mm|in|pt|pc)?")) ||
               lower.startsWith("calc(") || lower.startsWith("var(")
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
                char == '/' && depth == 0 -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                    tokens.add("/")
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
