package app.parsing.css.properties.shorthands

/**
 * Expands the `grid` shorthand property.
 *
 * This is a complex shorthand that can specify:
 * - grid-template-rows / grid-template-columns
 * - grid-template-areas with row tracks
 * - grid-auto-flow with auto tracks
 */
object GridShorthandExpander : ShorthandExpander {
    private val globalKeywords = setOf("inherit", "initial", "unset", "revert", "revert-layer")

    override fun expand(value: String): Map<String, String> {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        if (lower in globalKeywords) {
            return mapOf(
                "grid-template-rows" to trimmed,
                "grid-template-columns" to trimmed,
                "grid-template-areas" to trimmed,
                "grid-auto-rows" to trimmed,
                "grid-auto-columns" to trimmed,
                "grid-auto-flow" to trimmed
            )
        }

        if (lower == "none") {
            return mapOf(
                "grid-template-rows" to "none",
                "grid-template-columns" to "none",
                "grid-template-areas" to "none"
            )
        }

        val result = mutableMapOf<String, String>()

        // Check for auto-flow syntax: "auto-flow [dense] <tracks> / <explicit-tracks>"
        // or "<explicit-tracks> / auto-flow [dense] <tracks>"
        if (lower.contains("auto-flow")) {
            val parts = trimmed.split("/").map { it.trim() }
            if (parts.size == 2) {
                val (first, second) = parts
                if (first.lowercase().contains("auto-flow")) {
                    // auto-flow in rows
                    val flowPart = first.replace(Regex("auto-flow\\s*", RegexOption.IGNORE_CASE), "").trim()
                    result["grid-auto-flow"] = if (first.lowercase().contains("dense")) "row dense" else "row"
                    if (flowPart.isNotEmpty() && flowPart.lowercase() != "dense") {
                        result["grid-auto-rows"] = flowPart.replace("dense", "").trim()
                    }
                    result["grid-template-columns"] = second
                } else {
                    // auto-flow in columns
                    result["grid-template-rows"] = first
                    val flowPart = second.replace(Regex("auto-flow\\s*", RegexOption.IGNORE_CASE), "").trim()
                    result["grid-auto-flow"] = if (second.lowercase().contains("dense")) "column dense" else "column"
                    if (flowPart.isNotEmpty() && flowPart.lowercase() != "dense") {
                        result["grid-auto-columns"] = flowPart.replace("dense", "").trim()
                    }
                }
                return result
            }
        }

        // Simple syntax: rows / columns
        val slashParts = trimmed.split("/").map { it.trim() }
        if (slashParts.size == 2) {
            result["grid-template-rows"] = slashParts[0]
            result["grid-template-columns"] = slashParts[1]
        } else {
            result["grid-template-rows"] = trimmed
        }

        return result
    }
}

/**
 * Expands the `grid-row` shorthand property.
 *
 * Syntax: grid-row: <start> / <end>?
 *
 * Examples:
 * - "1" → grid-row-start: 1
 * - "1 / 3" → grid-row-start: 1, grid-row-end: 3
 * - "span 2" → grid-row-start: span 2
 */
object GridRowExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = value.split("/").map { it.trim() }

        return when (parts.size) {
            1 -> mapOf("grid-row-start" to parts[0])
            2 -> mapOf(
                "grid-row-start" to parts[0],
                "grid-row-end" to parts[1]
            )
            else -> emptyMap()
        }
    }
}

/**
 * Expands the `grid-column` shorthand property.
 *
 * Syntax: grid-column: <start> / <end>?
 *
 * Examples:
 * - "1" → grid-column-start: 1
 * - "1 / 3" → grid-column-start: 1, grid-column-end: 3
 * - "span 2" → grid-column-start: span 2
 */
object GridColumnExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = value.split("/").map { it.trim() }

        return when (parts.size) {
            1 -> mapOf("grid-column-start" to parts[0])
            2 -> mapOf(
                "grid-column-start" to parts[0],
                "grid-column-end" to parts[1]
            )
            else -> emptyMap()
        }
    }
}

/**
 * Expands the `grid-area` shorthand property.
 *
 * Syntax: grid-area: <row-start> / <column-start>? / <row-end>? / <column-end>?
 *
 * Examples:
 * - "header" → grid-row-start: header (named area)
 * - "1 / 2" → grid-row-start: 1, grid-column-start: 2
 * - "1 / 2 / 3 / 4" → all four values
 */
object GridAreaExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = value.split("/").map { it.trim() }

        return when (parts.size) {
            1 -> mapOf("grid-row-start" to parts[0])
            2 -> mapOf(
                "grid-row-start" to parts[0],
                "grid-column-start" to parts[1]
            )
            3 -> mapOf(
                "grid-row-start" to parts[0],
                "grid-column-start" to parts[1],
                "grid-row-end" to parts[2]
            )
            4 -> mapOf(
                "grid-row-start" to parts[0],
                "grid-column-start" to parts[1],
                "grid-row-end" to parts[2],
                "grid-column-end" to parts[3]
            )
            else -> emptyMap()
        }
    }
}

/**
 * Expands the `grid-template` shorthand property.
 *
 * Simplified: only handles "rows / columns" syntax.
 */
object GridTemplateExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = value.split("/").map { it.trim() }

        return when (parts.size) {
            1 -> mapOf("grid-template-rows" to parts[0])
            2 -> mapOf(
                "grid-template-rows" to parts[0],
                "grid-template-columns" to parts[1]
            )
            else -> emptyMap()
        }
    }
}

/**
 * Expands the `grid-gap` shorthand property (deprecated, use `gap`).
 */
object GridGapExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = value.trim().split("""\s+""".toRegex())

        return when (parts.size) {
            1 -> mapOf(
                "row-gap" to parts[0],
                "column-gap" to parts[0]
            )
            2 -> mapOf(
                "row-gap" to parts[0],
                "column-gap" to parts[1]
            )
            else -> emptyMap()
        }
    }
}
