package app.parsing.css.properties.shorthands

/**
 * Expands `mask` shorthand into longhands.
 *
 * mask shorthand: <mask-layer>#
 * where mask-layer = <mask-reference> || <position> [ / <bg-size> ]? || <repeat-style> ||
 *                    <geometry-box> || [ <geometry-box> | no-clip ] || <compositing-operator> || <masking-mode>
 */
object MaskExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed in setOf("inherit", "initial", "unset", "revert", "none")) {
            return mapOf(
                "mask-image" to trimmed,
                "mask-position" to if (trimmed == "none") "0% 0%" else trimmed,
                "mask-size" to if (trimmed == "none") "auto" else trimmed,
                "mask-repeat" to if (trimmed == "none") "repeat" else trimmed,
                "mask-origin" to if (trimmed == "none") "border-box" else trimmed,
                "mask-clip" to if (trimmed == "none") "border-box" else trimmed,
                "mask-composite" to if (trimmed == "none") "add" else trimmed,
                "mask-mode" to if (trimmed == "none") "match-source" else trimmed
            )
        }

        // Split layers by comma (respecting parentheses)
        val layers = splitByComma(trimmed)
        val result = mutableMapOf<String, String>()

        val images = mutableListOf<String>()
        val positions = mutableListOf<String>()
        val sizes = mutableListOf<String>()
        val repeats = mutableListOf<String>()

        for (layer in layers) {
            val parsed = parseLayer(layer)
            if (parsed["mask-image"] != null) images.add(parsed["mask-image"]!!)
            if (parsed["mask-position"] != null) positions.add(parsed["mask-position"]!!)
            if (parsed["mask-size"] != null) sizes.add(parsed["mask-size"]!!)
            if (parsed["mask-repeat"] != null) repeats.add(parsed["mask-repeat"]!!)
        }

        if (images.isNotEmpty()) result["mask-image"] = images.joinToString(", ")
        if (positions.isNotEmpty()) result["mask-position"] = positions.joinToString(", ")
        if (sizes.isNotEmpty()) result["mask-size"] = sizes.joinToString(", ")
        if (repeats.isNotEmpty()) result["mask-repeat"] = repeats.joinToString(", ")

        return result.ifEmpty { mapOf("mask-image" to trimmed) }
    }

    private fun parseLayer(layer: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val parts = splitByWhitespace(layer)

        var i = 0
        while (i < parts.size) {
            val part = parts[i]

            when {
                // Image reference (url or gradient)
                isImageReference(part) -> {
                    result["mask-image"] = part
                }
                // Repeat keywords
                part in setOf("repeat", "repeat-x", "repeat-y", "no-repeat", "space", "round") -> {
                    result["mask-repeat"] = part
                }
                // Position/size separator
                part == "/" -> {
                    if (i + 1 < parts.size) {
                        result["mask-size"] = parts[i + 1]
                        i++
                    }
                }
                // Position keywords
                part in setOf("top", "bottom", "left", "right", "center") ||
                    part.endsWith("%") || part.matches(Regex("\\d+(\\.\\d+)?(px|em|rem|%)")) -> {
                    val existing = result["mask-position"]
                    result["mask-position"] = if (existing != null) "$existing $part" else part
                }
            }
            i++
        }

        return result
    }

    private fun isImageReference(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("url(") ||
            lower.startsWith("linear-gradient(") ||
            lower.startsWith("radial-gradient(") ||
            lower.startsWith("conic-gradient(") ||
            lower.startsWith("repeating-linear-gradient(") ||
            lower.startsWith("repeating-radial-gradient(") ||
            lower.startsWith("repeating-conic-gradient(")
    }

    private fun splitByComma(value: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (char in value) {
            when {
                char == '(' -> { depth++; current.append(char) }
                char == ')' -> { depth--; current.append(char) }
                char == ',' && depth == 0 -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) result.add(current.toString().trim())
        return result
    }

    private fun splitByWhitespace(value: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0

        for (char in value) {
            when {
                char == '(' -> { depth++; current.append(char) }
                char == ')' -> { depth--; current.append(char) }
                char.isWhitespace() && depth == 0 -> {
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
