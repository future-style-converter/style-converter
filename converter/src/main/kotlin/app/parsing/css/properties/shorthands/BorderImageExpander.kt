package app.parsing.css.properties.shorthands

/**
 * Expands the `border-image` shorthand property.
 *
 * Syntax: border-image: <source> <slice> [/ <width> [/ <outset>]] <repeat>
 *
 * Examples:
 * - "url(border.png) 30 round" → source=url(border.png), slice=30, repeat=round
 * - "linear-gradient(red, blue) 30" → source=linear-gradient(red, blue), slice=30
 * - "url(border.png) 30 / 10px round" → source, slice=30, width=10px, repeat=round
 * - "url(border.png) 30 / 10px / 5px round" → all components
 */
object BorderImageExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, String>()

        // Check for global keywords
        if (trimmed.lowercase() in setOf("none", "inherit", "initial", "unset", "revert", "revert-layer")) {
            result["border-image-source"] = trimmed
            return result
        }

        // Parse the complex value
        val parsed = parseBorderImage(trimmed)
        parsed["source"]?.let { result["border-image-source"] = it }
        parsed["slice"]?.let { result["border-image-slice"] = it }
        parsed["width"]?.let { result["border-image-width"] = it }
        parsed["outset"]?.let { result["border-image-outset"] = it }
        parsed["repeat"]?.let { result["border-image-repeat"] = it }

        return result
    }

    private fun parseBorderImage(value: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // First, extract the source (url or gradient)
        val sourceMatch = extractSource(value)
        if (sourceMatch != null) {
            result["source"] = sourceMatch.first
            val remaining = sourceMatch.second.trim()
            if (remaining.isNotEmpty()) {
                parseSliceWidthOutsetRepeat(remaining, result)
            }
        } else {
            // No source, try to parse the rest
            parseSliceWidthOutsetRepeat(value, result)
        }

        return result
    }

    private fun extractSource(value: String): Pair<String, String>? {
        val lower = value.lowercase()

        // Check for url()
        if (lower.startsWith("url(")) {
            val endIndex = findMatchingParen(value, 3)
            if (endIndex > 0) {
                return Pair(value.substring(0, endIndex + 1), value.substring(endIndex + 1))
            }
        }

        // Check for gradient functions
        val gradientPrefixes = listOf(
            "linear-gradient(", "radial-gradient(", "conic-gradient(",
            "repeating-linear-gradient(", "repeating-radial-gradient(", "repeating-conic-gradient("
        )

        for (prefix in gradientPrefixes) {
            if (lower.startsWith(prefix)) {
                val startParen = value.indexOf('(')
                val endIndex = findMatchingParen(value, startParen)
                if (endIndex > 0) {
                    return Pair(value.substring(0, endIndex + 1), value.substring(endIndex + 1))
                }
            }
        }

        return null
    }

    private fun findMatchingParen(value: String, startIndex: Int): Int {
        var depth = 0
        for (i in startIndex until value.length) {
            when (value[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    private fun parseSliceWidthOutsetRepeat(value: String, result: MutableMap<String, String>) {
        val repeatKeywords = setOf("stretch", "repeat", "round", "space")

        // Split by "/" to separate slice / width / outset
        val slashParts = value.split("/").map { it.trim() }

        // First part contains slice and possibly repeat
        if (slashParts.isNotEmpty() && slashParts[0].isNotEmpty()) {
            val firstPart = slashParts[0]
            val tokens = splitTokens(firstPart)

            val sliceTokens = mutableListOf<String>()
            val repeatTokens = mutableListOf<String>()

            for (token in tokens) {
                val tokenLower = token.lowercase()
                if (tokenLower in repeatKeywords) {
                    repeatTokens.add(token)
                } else if (tokenLower == "fill" || isNumberOrPercent(token)) {
                    sliceTokens.add(token)
                }
            }

            if (sliceTokens.isNotEmpty()) {
                result["slice"] = sliceTokens.joinToString(" ")
            }
            if (repeatTokens.isNotEmpty()) {
                result["repeat"] = repeatTokens.joinToString(" ")
            }
        }

        // Second part is width
        if (slashParts.size > 1 && slashParts[1].isNotEmpty()) {
            val tokens = splitTokens(slashParts[1])
            val widthTokens = tokens.filter { isLengthOrNumber(it) }
            if (widthTokens.isNotEmpty()) {
                result["width"] = widthTokens.joinToString(" ")
            }
            // Also check for repeat keywords after width
            val repeatTokens = tokens.filter { it.lowercase() in repeatKeywords }
            if (repeatTokens.isNotEmpty() && result["repeat"] == null) {
                result["repeat"] = repeatTokens.joinToString(" ")
            }
        }

        // Third part is outset
        if (slashParts.size > 2 && slashParts[2].isNotEmpty()) {
            val tokens = splitTokens(slashParts[2])
            val outsetTokens = tokens.filter { isLengthOrNumber(it) }
            if (outsetTokens.isNotEmpty()) {
                result["outset"] = outsetTokens.joinToString(" ")
            }
            // Also check for repeat keywords after outset
            val repeatTokens = tokens.filter { it.lowercase() in repeatKeywords }
            if (repeatTokens.isNotEmpty() && result["repeat"] == null) {
                result["repeat"] = repeatTokens.joinToString(" ")
            }
        }
    }

    private fun splitTokens(value: String): List<String> {
        return value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun isNumberOrPercent(token: String): Boolean {
        return token.matches(Regex("^\\d+\\.?\\d*%?$"))
    }

    private fun isLengthOrNumber(token: String): Boolean {
        val lower = token.lowercase()
        return lower == "auto" ||
               token.matches(Regex("^\\d+\\.?\\d*(px|em|rem|%|pt|cm|mm|in|pc|ex|ch|vw|vh)?$"))
    }
}
